// Estructura de la aplicación: pantalla de login si no hay sesión, o la app con sus
// dos vistas (Perfil y Vitrina) si el usuario ya entró.
import { useState } from 'react'
import { cuentaActual, iniciarSesion, cerrarSesion } from './auth'
import Perfil from './Perfil'
import Vitrina from './Vitrina'

export default function App() {
  const cuenta = cuentaActual()
  const [vista, setVista] = useState('vitrina')

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
        </nav>
        <div className="sesion">
          <span>{cuenta.username}</span>
          <button className="secundario" onClick={cerrarSesion}>Cerrar sesión</button>
        </div>
      </header>

      <main>{vista === 'perfil' ? <Perfil /> : <Vitrina />}</main>
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
