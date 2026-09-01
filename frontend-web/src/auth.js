// Autenticación contra Azure AD (Microsoft Entra ID).
//
// Concentra todo lo relativo a MSAL: la configuración del cliente y la obtención del token
// que después se manda al BFF. Ningún otro archivo habla con MSAL directamente.
//
// El flujo es Authorization Code + PKCE. No hay client secret porque un SPA no puede guardarlo:
// el código corre en el navegador a la vista de cualquiera. PKCE es justamente lo que reemplaza
// al secreto en clientes públicos.

import { PublicClientApplication, InteractionRequiredAuthError, EventType } from '@azure/msal-browser'

export const configuracionMsal = {
  auth: {
    clientId: import.meta.env.VITE_AZURE_CLIENT_ID,
    authority: `https://login.microsoftonline.com/${import.meta.env.VITE_AZURE_TENANT_ID}`,
    redirectUri: window.location.origin,
  },
  cache: {
    // sessionStorage y no localStorage: el token se borra al cerrar la pestaña.
    // Es lo que protege la cuenta en los computadores compartidos del taller (HU-03).
    cacheLocation: 'sessionStorage',
    storeAuthStateInCookie: false,
  },
}

// El scope de la API, no los de OIDC. Pedir solo "openid profile" devuelve un ID token
// cuya audiencia es el SPA, y el ValidadorAudiencia del backend lo rechaza con 401.
export const solicitudDeToken = { scopes: [import.meta.env.VITE_AZURE_SCOPE] }

export const msal = new PublicClientApplication(configuracionMsal)

/** Prepara MSAL. Hay que llamarlo una vez antes de usar cualquier otra función de este archivo. */
export async function inicializar() {
  await msal.initialize()
  await msal.handleRedirectPromise()
}

/** Devuelve la cuenta que inició sesión, o null si todavía no entró nadie. */
export function cuentaActual() {
  return msal.getAllAccounts()[0] ?? null
}

/** Manda al usuario a la pantalla de login institucional. */
export function iniciarSesion() {
  return msal.loginRedirect(solicitudDeToken)
}

/** Cierra la sesión y limpia el token del navegador. */
export function cerrarSesion() {
  return msal.logoutRedirect({ account: cuentaActual() })
}

/**
 * Devuelve un access token válido para llamar al BFF.
 *
 * Primero lo intenta en silencio, usando el token en caché o renovándolo sin molestar al usuario.
 * Solo si Azure pide intervención humana (consentimiento, MFA, sesión vencida) lo manda al login.
 */
export async function obtenerToken() {
  const cuenta = cuentaActual()
  if (!cuenta) return iniciarSesion()

  try {
    const respuesta = await msal.acquireTokenSilent({ ...solicitudDeToken, account: cuenta })
    return respuesta.accessToken
  } catch (error) {
    if (error instanceof InteractionRequiredAuthError) {
      return msal.acquireTokenRedirect(solicitudDeToken)
    }
    throw error
  }
}

/**
 * Devuelve el contenido del access token ya decodificado.
 *
 * Un JWT son tres partes separadas por punto; la del medio son los datos, codificados en
 * base64url. Acá solo se leen: la firma la verifica el backend, que es quien tiene las claves
 * públicas de Microsoft. Lo que el navegador decodifica sirve para mostrar información, nunca
 * para decidir permisos.
 */
export async function claimsDelToken() {
  const token = await obtenerToken()
  const cuerpo = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
  return JSON.parse(decodeURIComponent(escape(atob(cuerpo))))
}

/**
 * Roles que Azure AD asignó a la persona, leídos del claim 'roles'.
 *
 * Viene vacío cuando el tenant no usa App Roles: en ese caso el rol lo deduce el backend a
 * partir del dominio del correo. Esa regla no se duplica acá a propósito — si viviera en los
 * dos lados, tarde o temprano se desincronizarían.
 */
export async function rolesDelToken() {
  const claims = await claimsDelToken()
  const roles = claims.roles ?? []
  return roles.map((r) => r.toUpperCase())
}

/** Permisos que el token habilita sobre la API, leídos del claim 'scp'. */
export async function scopesDelToken() {
  const claims = await claimsDelToken()
  return (claims.scp ?? '').split(' ').filter(Boolean)
}

/**
 * Avisa cuando cambia la sesión: alguien entró, salió, o el token dejó de renovarse.
 *
 * Sin esto la pantalla se queda como estaba aunque la sesión ya no sirva, y la persona ve
 * vistas vacías sin entender por qué. Devuelve una función para dejar de escuchar.
 */
export function alCambiarSesion(callback) {
  const id = msal.addEventCallback((evento) => {
    const relevantes = [
      EventType.LOGIN_SUCCESS,
      EventType.LOGOUT_SUCCESS,
      EventType.ACQUIRE_TOKEN_SUCCESS,
      EventType.ACQUIRE_TOKEN_FAILURE,
    ]
    if (relevantes.includes(evento.eventType)) callback(cuentaActual())
  })
  return () => { if (id) msal.removeEventCallback(id) }
}
