// Autenticación contra Azure AD (Microsoft Entra ID).
//
// Concentra todo lo relativo a MSAL: la configuración del cliente y la obtención del token
// que después se manda al BFF. Ningún otro archivo habla con MSAL directamente.
//
// El flujo es Authorization Code + PKCE. No hay client secret porque un SPA no puede guardarlo:
// el código corre en el navegador a la vista de cualquiera. PKCE es justamente lo que reemplaza
// al secreto en clientes públicos.

import { PublicClientApplication, InteractionRequiredAuthError } from '@azure/msal-browser'

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
