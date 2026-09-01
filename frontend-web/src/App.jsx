// Estructura de la aplicación: pantalla de login si no hay sesión, o la app con sus
// dos vistas (Perfil y Vitrina) si el usuario ya entró.
import { useEffect, useState } from 'react'
import { cuentaActual, iniciarSesion, cerrarSesion, claimsDelToken, rolesDelToken, alCambiarSesion } from './auth'
import Perfil from './Perfil'
import Vitrina from './Vitrina'

export default function App() {
  // La cuenta se guarda en estado y no se lee en cada render: así, cuando la sesión se pierde
  // o se cierra, la aplicación vuelve sola al login en vez de quedar mostrando vistas vacías.
  const [cuenta, setCuenta] = useState(cuentaActual())
  const [vista, setVista] = useState('vitrina')

  useEffect(() => alCambiarSesion(setCuenta), [])

  if (!cuenta) return <Login />

  return (
    <div className="app">
      <header>
        <div className="marca">
          <span className="logo" />
          <strong>DuocConecta</strong>
        </div>
        <nav>
          <button className={vista === 'vitrina' ? 'activo' : ''} onClick={() => setVista('vitrina')}>
            Vitrina
          </button>
          <button className={vista === 'perfil' ? 'activo' : ''} onClick={() => setVista('perfil')}>
            Mi perfil
          </button>
          <button className={vista === 'token' ? 'activo' : ''} onClick={() => setVista('token')}>
            Diagnóstico
          </button>
        </nav>
        <div className="sesion">
          <span>{cuenta.username}</span>
          <RolDelUsuario />
          <button className="secundario" onClick={cerrarSesion}>Cerrar sesión</button>
        </div>
      </header>

      <main>
        {vista === 'perfil' && <Perfil />}
        {vista === 'token' && <Diagnostico />}
        {vista === 'vitrina' && <Vitrina />}
      </main>
    </div>
  )
}

/** Pantalla de entrada. El botón dispara el flujo Authorization Code + PKCE contra Azure AD. */
function Login() {
  return (
    <div className="login">
      <div className="login-panel">
        <span className="logo" />
        <h1>Los proyectos se muestran.<br />Los contactos se piden.</h1>
        <p>
          Publicá lo que estás haciendo, encontrá gente de otras carreras y sedes, y compartí
          tus datos solo con quien vos aceptás.
        </p>
      </div>
      <div className="login-form">
        <h2>Entrá con tu cuenta Duoc</h2>
        <p className="ayuda">
          El acceso se valida con el login institucional. Solo se permiten correos de
          dominios de Duoc UC.
        </p>
        <button className="principal" onClick={iniciarSesion}>
          Entrar con Microsoft
        </button>
      </div>
    </div>
  )
}

/**
 * Muestra el contenido del token que devuelve Azure AD.
 *
 * Sirve para comprobar de un vistazo que el login trae lo que el backend necesita: la audiencia
 * correcta, el identificador del usuario y sobre todo el correo institucional, del que se deduce
 * el rol. Si el correo no llega, el backend responde 403 y sin esta pantalla no se sabe por qué.
 */
function Diagnostico() {
  const [claims, setClaims] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    // La decodificación vive en auth.js, junto al resto del manejo del token.
    claimsDelToken().then(setClaims).catch((e) => setError(e.message))
  }, [])

  if (error) return <div className="error">{error}</div>
  if (!claims) return <div className="cargando">Pidiendo el token…</div>

  const importantes = ['aud', 'iss', 'oid', 'email', 'preferred_username', 'upn', 'roles', 'scp']

  return (
    <section className="tarjeta">
      <h2>Diagnóstico del token</h2>
      <p className="ayuda">Lo que Azure AD le entrega al backend en cada petición.</p>

      <table className="claims">
        <tbody>
          {importantes.map((c) => (
            <tr key={c}>
              <th>{c}</th>
              <td>{claims[c] ? JSON.stringify(claims[c]) : <em>no viene</em>}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <details>
        <summary>Ver el token completo</summary>
        <pre>{JSON.stringify(claims, null, 2)}</pre>
      </details>
    </section>
  )
}

/**
 * Muestra el rol de la persona, leído de los claims del token.
 *
 * Azure AD entrega los App Roles en el claim `roles`. Cuando el tenant no los usa, ese claim
 * viene vacío y el rol lo deduce el backend a partir del dominio del correo: en ese caso acá
 * no se muestra nada y el rol aparece igual en la pantalla de perfil. La regla de dominios no
 * se repite en el navegador a propósito, para que no pueda quedar desincronizada del backend.
 */
function RolDelUsuario() {
  const [roles, setRoles] = useState([])

  useEffect(() => {
    rolesDelToken().then(setRoles).catch(() => setRoles([]))
  }, [])

  if (roles.length === 0) return null
  return <span className="insignia-rol">{roles.join(' · ')}</span>
}
