package edu.pe.cibertec.taller.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.pe.cibertec.taller.excepcion.CitaNoCancelableException;
import edu.pe.cibertec.taller.excepcion.CitaNoEncontradaException;
import edu.pe.cibertec.taller.excepcion.EspecialidadIncorrectaException;
import edu.pe.cibertec.taller.excepcion.FechaInvalidaException;
import edu.pe.cibertec.taller.excepcion.HorarioNoPermitidoException;
import edu.pe.cibertec.taller.excepcion.MecanicoNoEncontradoException;
import edu.pe.cibertec.taller.excepcion.SinDisponibilidadException;
import edu.pe.cibertec.taller.modelo.Cita;
import edu.pe.cibertec.taller.modelo.EstadoCita;
import edu.pe.cibertec.taller.modelo.Mecanico;
import edu.pe.cibertec.taller.modelo.ResultadoCancelacion;
import edu.pe.cibertec.taller.modelo.TipoServicio;
import edu.pe.cibertec.taller.repositorio.RepositorioCitas;
import edu.pe.cibertec.taller.repositorio.RepositorioMecanicos;
import edu.pe.cibertec.taller.servicio.impl.ServicioCitasImpl;
import edu.pe.cibertec.taller.util.ProveedorFechaHora;
import edu.pe.cibertec.taller.util.ServicioNotificaciones;
import java.time.LocalDateTime;
import java.util.List;
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

	// ==========================================================
	// PREGUNTA 02: Horario de los servicios pesados
	// ==========================================================

	@Test
	@DisplayName("PREGUNTA 02 - REPARACION_MOTOR a las 07:00 se rechaza con HorarioNoPermitidoException")
	void reparacionMotorALasSiete() {
		// Arrange
		Mecanico mecanicoZafiro = new Mecanico(1L, NOMBRE_MECANICO, TipoServicio.REPARACION_MOTOR);
		when(repositorioMecanicos.findById(1L)).thenReturn(Optional.of(mecanicoZafiro));

		// Act y Assert
		assertThrows(HorarioNoPermitidoException.class,
				() -> servicioCitas.agendarCita(1L, PLACA, TipoServicio.REPARACION_MOTOR, elDiaALas(7)));
		verify(repositorioCitas, never()).save(any(Cita.class));
	}

	@Test
	@DisplayName("PREGUNTA 02 - REPARACION_MOTOR a las 08:00 se acepta y queda PROGRAMADA")
	void reparacionMotorALasOcho() {
		// Arrange
		Mecanico mecanicoZafiro = new Mecanico(1L, NOMBRE_MECANICO, TipoServicio.REPARACION_MOTOR);
		when(repositorioMecanicos.findById(1L)).thenReturn(Optional.of(mecanicoZafiro));
		when(repositorioCitas.save(any(Cita.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

		// Act
		Cita citaRegistrada = servicioCitas.agendarCita(1L, PLACA, TipoServicio.REPARACION_MOTOR, elDiaALas(8));

		// Assert
		assertEquals(EstadoCita.PROGRAMADA, citaRegistrada.getEstado());
		assertEquals(4, citaRegistrada.getDuracionHoras());
		verify(repositorioCitas, times(1)).save(any(Cita.class));
		verify(servicioNotificaciones, times(1)).notificarCitaAgendada(any(Cita.class));
	}

	@Test
	@DisplayName("PREGUNTA 02 - REPARACION_MOTOR a las 11:00 se acepta y queda PROGRAMADA")
	void reparacionMotorALasOnce() {
		// Arrange
		Mecanico mecanicoZafiro = new Mecanico(1L, NOMBRE_MECANICO, TipoServicio.REPARACION_MOTOR);
		when(repositorioMecanicos.findById(1L)).thenReturn(Optional.of(mecanicoZafiro));
		when(repositorioCitas.save(any(Cita.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

		// Act
		Cita citaRegistrada = servicioCitas.agendarCita(1L, PLACA, TipoServicio.REPARACION_MOTOR, elDiaALas(11));

		// Assert
		assertEquals(EstadoCita.PROGRAMADA, citaRegistrada.getEstado());
		assertEquals(4, citaRegistrada.getDuracionHoras());
		verify(repositorioCitas, times(1)).save(any(Cita.class));
		verify(servicioNotificaciones, times(1)).notificarCitaAgendada(any(Cita.class));
	}

	@Test
	@DisplayName("PREGUNTA 02 - REPARACION_MOTOR a las 12:00 se rechaza con HorarioNoPermitidoException")
	void reparacionMotorALasDoce() {
		// Arrange
		Mecanico mecanicoZafiro = new Mecanico(1L, NOMBRE_MECANICO, TipoServicio.REPARACION_MOTOR);
		when(repositorioMecanicos.findById(1L)).thenReturn(Optional.of(mecanicoZafiro));

		// Act y Assert
		assertThrows(HorarioNoPermitidoException.class,
				() -> servicioCitas.agendarCita(1L, PLACA, TipoServicio.REPARACION_MOTOR, elDiaALas(12)));
		verify(repositorioCitas, never()).save(any(Cita.class));
	}

	// ==========================================================
	// PREGUNTA 03: Cancelacion de citas
	// ==========================================================

	@Test
	@DisplayName("PREGUNTA 03 - Cancelar con exactamente 24 horas de anticipacion no aplica penalidad y queda CANCELADA")
	void cancelarConVeinticuatroHorasExactas() {
		// Arrange
		Cita citaZafiro = new Cita();
		citaZafiro.setId(1L);
		citaZafiro.setMecanico(new Mecanico(1L, NOMBRE_MECANICO, TipoServicio.CAMBIO_ACEITE));
		citaZafiro.setPlacaVehiculo(PLACA);
		citaZafiro.setTipoServicio(TipoServicio.CAMBIO_ACEITE);
		citaZafiro.setFechaHoraInicio(elDiaALas(10));
		citaZafiro.setDuracionHoras(1);
		citaZafiro.setEstado(EstadoCita.PROGRAMADA);
		when(repositorioCitas.findById(1L)).thenReturn(Optional.of(citaZafiro));
		// Exactamente 24 h antes de las 10:00 del DIA -> el limite cae justo en el inicio
		when(proveedorFechaHora.ahora()).thenReturn(LocalDateTime.of(2026, 9, 9, 10, 0));

		// Act
		ResultadoCancelacion resultado = servicioCitas.cancelarCita(1L);

		// Assert
		assertTrue(resultado.isExitoso());
		assertEquals(0.0, resultado.getMontoPenalidad());
		assertEquals(EstadoCita.CANCELADA, citaZafiro.getEstado());
		verify(servicioNotificaciones, times(1)).notificarCitaCancelada(citaZafiro);
	}

	@Test
	@DisplayName("PREGUNTA 03 - Cancelar con 2 horas de anticipacion aplica una penalidad de 50.00")
	void cancelarConDosHorasDeAnticipacion() {
		// Arrange
		Cita citaZafiro = new Cita();
		citaZafiro.setId(1L);
		citaZafiro.setMecanico(new Mecanico(1L, NOMBRE_MECANICO, TipoServicio.CAMBIO_ACEITE));
		citaZafiro.setPlacaVehiculo(PLACA);
		citaZafiro.setTipoServicio(TipoServicio.CAMBIO_ACEITE);
		citaZafiro.setFechaHoraInicio(elDiaALas(10));
		citaZafiro.setDuracionHoras(1);
		citaZafiro.setEstado(EstadoCita.PROGRAMADA);
		when(repositorioCitas.findById(1L)).thenReturn(Optional.of(citaZafiro));
		// 2 h antes de las 10:00 del DIA
		when(proveedorFechaHora.ahora()).thenReturn(elDiaALas(8));

		// Act
		ResultadoCancelacion resultado = servicioCitas.cancelarCita(1L);

		// Assert
		assertEquals(50.0, resultado.getMontoPenalidad());
		assertEquals(EstadoCita.CANCELADA, citaZafiro.getEstado());
	}

	@Test
	@DisplayName("PREGUNTA 03 - Cancelar una cita ya ATENDIDA lanza CitaNoCancelableException y no guarda")
	void cancelarCitaYaAtendida() {
		// Arrange
		Cita citaZafiro = new Cita();
		citaZafiro.setId(1L);
		citaZafiro.setMecanico(new Mecanico(1L, NOMBRE_MECANICO, TipoServicio.CAMBIO_ACEITE));
		citaZafiro.setPlacaVehiculo(PLACA);
		citaZafiro.setTipoServicio(TipoServicio.CAMBIO_ACEITE);
		citaZafiro.setFechaHoraInicio(elDiaALas(10));
		citaZafiro.setDuracionHoras(1);
		citaZafiro.setEstado(EstadoCita.ATENDIDA);
		when(repositorioCitas.findById(1L)).thenReturn(Optional.of(citaZafiro));

		// Act y Assert
		assertThrows(CitaNoCancelableException.class, () -> servicioCitas.cancelarCita(1L));
		verify(repositorioCitas, never()).save(any(Cita.class));
	}

	// ==========================================================
	// PRUEBAS ADICIONALES: cobertura del resto de reglas del servicio
	// ==========================================================

	@Test
	@DisplayName("EXTRA - Agendar en una fecha del pasado lanza FechaInvalidaException y no guarda")
	void agendarConFechaEnElPasado() {
		// Arrange
		Mecanico mecanicoZafiro = new Mecanico(1L, NOMBRE_MECANICO, TipoServicio.CAMBIO_ACEITE);
		when(repositorioMecanicos.findById(1L)).thenReturn(Optional.of(mecanicoZafiro));
		// Anterior al reloj simulado (09/09/2026 08:00)
		LocalDateTime fechaPasada = LocalDateTime.of(2026, 9, 8, 10, 0);

		// Act y Assert
		assertThrows(FechaInvalidaException.class,
				() -> servicioCitas.agendarCita(1L, PLACA, TipoServicio.CAMBIO_ACEITE, fechaPasada));
		verify(repositorioCitas, never()).save(any(Cita.class));
	}

	@Test
	@DisplayName("EXTRA - Cancelar una cita inexistente lanza CitaNoEncontradaException y no guarda")
	void cancelarCitaInexistente() {
		// Arrange
		Long idCitaZafiro = 99L;
		when(repositorioCitas.findById(idCitaZafiro)).thenReturn(Optional.empty());

		// Act y Assert
		assertThrows(CitaNoEncontradaException.class, () -> servicioCitas.cancelarCita(idCitaZafiro));
		verify(repositorioCitas, never()).save(any(Cita.class));
	}

	@Test
	@DisplayName("EXTRA - Buscar mecanico disponible retorna el primero sin citas superpuestas")
	void buscarMecanicoDisponibleRetornaPrimeroLibre() {
		// Arrange
		Mecanico mecanicoOcupado = new Mecanico(1L, NOMBRE_MECANICO, TipoServicio.CAMBIO_ACEITE);
		Mecanico mecanicoLibreZafiro = new Mecanico(2L, NOMBRE_MECANICO, TipoServicio.CAMBIO_ACEITE);
		when(repositorioMecanicos.findByEspecialidad(TipoServicio.CAMBIO_ACEITE))
				.thenReturn(List.of(mecanicoOcupado, mecanicoLibreZafiro));
		Cita citaOcupada = new Cita();
		citaOcupada.setFechaHoraInicio(elDiaALas(10));
		citaOcupada.setDuracionHoras(1);
		citaOcupada.setEstado(EstadoCita.PROGRAMADA);
		when(repositorioCitas.findByMecanicoIdAndEstado(1L, EstadoCita.PROGRAMADA))
				.thenReturn(List.of(citaOcupada));
		when(repositorioCitas.findByMecanicoIdAndEstado(2L, EstadoCita.PROGRAMADA))
				.thenReturn(List.of());

		// Act
		Mecanico mecanicoDisponible = servicioCitas.buscarMecanicoDisponible(TipoServicio.CAMBIO_ACEITE, elDiaALas(10));

		// Assert
		assertEquals(mecanicoLibreZafiro, mecanicoDisponible);
	}

	@Test
	@DisplayName("EXTRA - Buscar mecanico cuando ninguno esta libre lanza SinDisponibilidadException")
	void buscarMecanicoSinDisponibilidad() {
		// Arrange
		Mecanico mecanicoOcupadoZafiro = new Mecanico(1L, NOMBRE_MECANICO, TipoServicio.CAMBIO_ACEITE);
		when(repositorioMecanicos.findByEspecialidad(TipoServicio.CAMBIO_ACEITE))
				.thenReturn(List.of(mecanicoOcupadoZafiro));
		Cita citaOcupada = new Cita();
		citaOcupada.setFechaHoraInicio(elDiaALas(10));
		citaOcupada.setDuracionHoras(1);
		citaOcupada.setEstado(EstadoCita.PROGRAMADA);
		when(repositorioCitas.findByMecanicoIdAndEstado(1L, EstadoCita.PROGRAMADA))
				.thenReturn(List.of(citaOcupada));

		// Act y Assert
		assertThrows(SinDisponibilidadException.class,
				() -> servicioCitas.buscarMecanicoDisponible(TipoServicio.CAMBIO_ACEITE, elDiaALas(10)));
	}
}
