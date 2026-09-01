// Cliente del BFF.
//
// El frontend solo habla con el BFF; nunca llama a los microservicios directamente.
// Cada petición lleva el access token de Azure AD en la cabecera Authorization.

import { obtenerToken } from './auth'

// Vacío significa "mismo origen". Es lo que se usa cuando el front va desplegado detrás del
// API Gateway, que sirve la aplicación y la API bajo la misma dirección: ahí las llamadas son
// relativas y el navegador ni siquiera aplica CORS. En local sí lleva la URL completa del BFF,
// porque el front corre en el puerto 5173 y el BFF en el 8080.
const BASE = import.meta.env.VITE_BFF_URL ?? ''

/** Hace una petición al BFF adjuntando el token y traduciendo los errores a algo legible. */
async function pedir(ruta, opciones = {}) {
  const token = await obtenerToken()

  const respuesta = await fetch(`${BASE}${ruta}`, {
    ...opciones,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      ...opciones.headers,
    },
  })

  if (respuesta.status === 401) {
    throw new Error('Tu sesión no es válida o venció. Volvé a iniciar sesión.')
  }
  if (respuesta.status === 403) {
    // El backend responde 403 cuando el correo no pertenece a un dominio institucional.
    const problema = await respuesta.json().catch(() => ({}))
    throw new Error(problema.detail ?? 'Tu cuenta no tiene permiso para esta operación.')
  }
  if (!respuesta.ok) {
    throw new Error(`El servidor respondió ${respuesta.status}.`)
  }
  return respuesta.status === 204 ? null : respuesta.json()
}

/** Perfil y redes del usuario autenticado, en una sola llamada agregada por el BFF. */
export const obtenerMiPerfil = () => pedir('/api/v1/bff/mi-perfil')

/** Guarda los cambios del perfil propio. */
export const guardarMiPerfil = (perfil) =>
  pedir('/api/v1/usuarios/me', { method: 'PUT', body: JSON.stringify(perfil) })

/** Muestra u oculta el perfil propio en las búsquedas. */
export const alternarVisibilidad = () =>
  pedir('/api/v1/usuarios/me/visibilidad', { method: 'PATCH' })

/**
 * Listado público de proyectos para la vitrina.
 * Devuelve una lista vacía si ms-proyectos todavía no está desplegado, para que la
 * pantalla muestre su estado vacío en vez de romperse.
 */
export const listarProyectos = async () => {
  try {
    return await pedir('/api/v1/proyectos')
  } catch {
    return []
  }
}
