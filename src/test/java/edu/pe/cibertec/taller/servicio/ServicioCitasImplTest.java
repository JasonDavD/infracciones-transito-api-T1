package edu.pe.cibertec.taller.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.pe.cibertec.taller.excepcion.EspecialidadIncorrectaException;
import edu.pe.cibertec.taller.excepcion.MecanicoNoEncontradoException;
import edu.pe.cibertec.taller.modelo.Cita;
import edu.pe.cibertec.taller.modelo.EstadoCita;
import edu.pe.cibertec.taller.modelo.Mecanico;
import edu.pe.cibertec.taller.modelo.TipoServicio;
import edu.pe.cibertec.taller.repositorio.RepositorioCitas;
import edu.pe.cibertec.taller.repositorio.RepositorioMecanicos;
import edu.pe.cibertec.taller.servicio.impl.ServicioCitasImpl;
import edu.pe.cibertec.taller.util.ProveedorFechaHora;
import edu.pe.cibertec.taller.util.ServicioNotificaciones;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServicioCitasImplTest {

	@Mock
	private RepositorioMecanicos repositorioMecanicos;

	@Mock
	private RepositorioCitas repositorioCitas;

	@Mock
	private ProveedorFechaHora proveedorFechaHora;

	@Mock
	private ServicioNotificaciones servicioNotificaciones;

	private ServicioCitasImpl servicioCitas;

	// Datos personales obligatorios en todas las pruebas
	private static final String PLACA = "DAV-540";
	private static final String NOMBRE_MECANICO = "Jason Dávila";

	// El reloj simulado se fija un dia antes del DIA (10/09/2026), a las 08:00
	private static final LocalDateTime AHORA = LocalDateTime.of(2026, 9, 9, 8, 0);

	@BeforeEach
	void inicializar() {
		servicioCitas = new ServicioCitasImpl(repositorioMecanicos, repositorioCitas,
				proveedorFechaHora, servicioNotificaciones);
		// lenient: no todos los tests llegan a usar el reloj (algunos lanzan antes)
		lenient().when(proveedorFechaHora.ahora()).thenReturn(AHORA);
	}

	// DIA: 10 de setiembre de 2026, a la hora indicada
	private LocalDateTime elDiaALas(int hora) {
		return LocalDateTime.of(2026, 9, 10, hora, 0);
	}

	// ==========================================================
	// PREGUNTA 01: Registro de citas
	// ==========================================================

	@Test
	@DisplayName("PREGUNTA 01 - Registrar un CAMBIO_ACEITE valido lo guarda, notifica una vez y lo retorna PROGRAMADA")
	void registrarCambioAceiteValido() {
		// Arrange
		Mecanico mecanicoZafiro = new Mecanico(1L, NOMBRE_MECANICO, TipoServicio.CAMBIO_ACEITE);
		when(repositorioMecanicos.findById(1L)).thenReturn(Optional.of(mecanicoZafiro));
		when(repositorioCitas.save(any(Cita.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

		// Act
		Cita citaRegistrada = servicioCitas.agendarCita(1L, PLACA, TipoServicio.CAMBIO_ACEITE, elDiaALas(10));

		// Assert
		assertEquals(EstadoCita.PROGRAMADA, citaRegistrada.getEstado());
		assertEquals(1, citaRegistrada.getDuracionHoras());
		assertEquals(PLACA, citaRegistrada.getPlacaVehiculo());
		verify(repositorioCitas, times(1)).save(any(Cita.class));
		verify(servicioNotificaciones, times(1)).notificarCitaAgendada(any(Cita.class));
	}

	@Test
	@DisplayName("PREGUNTA 01 - Registrar con un mecanico inexistente (id 99) lanza MecanicoNoEncontradoException y no guarda")
	void registrarConMecanicoInexistente() {
		// Arrange
		Long idMecanicoZafiro = 99L;
		when(repositorioMecanicos.findById(idMecanicoZafiro)).thenReturn(Optional.empty());

		// Act y Assert
		assertThrows(MecanicoNoEncontradoException.class,
				() -> servicioCitas.agendarCita(idMecanicoZafiro, PLACA, TipoServicio.CAMBIO_ACEITE, elDiaALas(10)));
		verify(repositorioCitas, never()).save(any(Cita.class));
	}

	@Test
	@DisplayName("PREGUNTA 01 - Registrar REPARACION_MOTOR con mecanico de especialidad CAMBIO_ACEITE lanza EspecialidadIncorrectaException y no guarda")
	void registrarConEspecialidadIncorrecta() {
		// Arrange
		Mecanico mecanicoZafiro = new Mecanico(1L, NOMBRE_MECANICO, TipoServicio.CAMBIO_ACEITE);
		when(repositorioMecanicos.findById(1L)).thenReturn(Optional.of(mecanicoZafiro));

		// Act y Assert
		assertThrows(EspecialidadIncorrectaException.class,
				() -> servicioCitas.agendarCita(1L, PLACA, TipoServicio.REPARACION_MOTOR, elDiaALas(10)));
		verify(repositorioCitas, never()).save(any(Cita.class));
	}
}
