// Pantalla de perfil propio.
//
// Lee de GET /api/v1/bff/mi-perfil, que el BFF arma juntando el perfil y las redes en una
// sola respuesta. La primera vez que alguien entra, esa llamada crea el perfil sola
// (auto-aprovisionamiento) a partir de los datos del token.
import { useEffect, useState } from 'react'
import { obtenerMiPerfil, guardarMiPerfil, alternarVisibilidad } from './api'

export default function Perfil() {
  const [datos, setDatos] = useState(null)
  const [error, setError] = useState(null)
  const [guardando, setGuardando] = useState(false)

  useEffect(() => {
    obtenerMiPerfil().then(setDatos).catch((e) => setError(e.message))
  }, [])

  if (error) return <div className="error">{error}</div>
  if (!datos) return <div className="cargando">Cargando tu perfil…</div>

  const { perfil, redes, perfilIncompleto } = datos

  const enviar = async (evento) => {
    evento.preventDefault()
    setGuardando(true)
    setError(null)
    const f = new FormData(evento.target)
    try {
      await guardarMiPerfil({
        nombre: f.get('nombre'),
        carrera: f.get('carrera'),
        sede: f.get('sede'),
        bio: f.get('bio'),
        telefono: f.get('telefono'),
        // Una red por línea, que es más cómodo de escribir que un JSON.
        redes: f.get('redes').split('\n').map((r) => r.trim()).filter(Boolean),
      })
      setDatos(await obtenerMiPerfil())
    } catch (e) {
      setError(e.message)
    } finally {
      setGuardando(false)
    }
  }

  return (
    <section className="tarjeta">
      <h2>Mi perfil</h2>
      <p className="ayuda">
        <strong>{perfil.correo}</strong> · rol <strong>{perfil.rol}</strong> ·{' '}
        {perfil.visible ? 'visible en las búsquedas' : 'oculto de las búsquedas'}
      </p>

      {perfilIncompleto && (
        <div className="aviso">
          Te faltan datos por completar. Con carrera, sede y una breve biografía tu perfil
          aparece en la vitrina y es más fácil que te encuentren.
        </div>
      )}

      <form onSubmit={enviar}>
        <label>Nombre<input name="nombre" defaultValue={perfil.nombre} required /></label>
        <label>Carrera<input name="carrera" defaultValue={perfil.carrera ?? ''} /></label>
        <label>Sede<input name="sede" defaultValue={perfil.sede ?? ''} /></label>
        <label>Biografía<textarea name="bio" rows="3" defaultValue={perfil.bio ?? ''} /></label>

        <fieldset>
          <legend>Datos de contacto — privados</legend>
          <p className="ayuda">
            No se muestran a nadie. Se comparten solo cuando aceptás una solicitud de colaboración.
          </p>
          <label>Teléfono<input name="telefono" defaultValue={perfil.telefono ?? ''} /></label>
          <label>
            Redes, una por línea
            <textarea name="redes" rows="3" defaultValue={redes.join('\n')} />
          </label>
        </fieldset>

        <div className="acciones">
          <button className="principal" type="submit" disabled={guardando}>
            {guardando ? 'Guardando…' : 'Guardar cambios'}
          </button>
          <button
            type="button"
            className="secundario"
            onClick={async () => {
              await alternarVisibilidad()
              setDatos(await obtenerMiPerfil())
            }}
          >
            {perfil.visible ? 'Ocultar mi perfil' : 'Mostrar mi perfil'}
          </button>
        </div>
      </form>
    </section>
  )
}
