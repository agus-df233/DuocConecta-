// Vitrina de proyectos.
//
// Depende de ms-proyectos, que todavía no está construido. Mientras tanto muestra un estado
// vacío en vez de romperse: así el frontend se puede desarrollar y demostrar sin esperar
// a que ese microservicio exista.
import { useEffect, useState } from 'react'
import { listarProyectos } from './api'

export default function Vitrina() {
  const [proyectos, setProyectos] = useState(null)

  useEffect(() => {
    listarProyectos().then(setProyectos)
  }, [])

  if (proyectos === null) return <div className="cargando">Cargando la vitrina…</div>

  if (proyectos.length === 0) {
    return (
      <section className="tarjeta vacio">
        <h2>Todavía no hay proyectos publicados</h2>
        <p className="ayuda">
          Cuando el servicio de proyectos esté desplegado, acá van a aparecer los trabajos de la
          comunidad, con filtros por carrera y sede.
        </p>
      </section>
    )
  }

  return (
    <section>
      <h2>Vitrina de proyectos</h2>
      <div className="grilla">
        {proyectos.map((p) => (
          <article key={p.id} className="tarjeta">
            <h3>{p.titulo}</h3>
            <p>{p.descripcion}</p>
            <p className="ayuda">{p.carrera} · {p.sede}</p>
          </article>
        ))}
      </div>
    </section>
  )
}
