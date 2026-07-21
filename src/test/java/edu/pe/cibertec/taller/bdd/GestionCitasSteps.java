package edu.pe.cibertec.taller.bdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.pe.cibertec.taller.excepcion.HorarioOcupadoException;
import edu.pe.cibertec.taller.modelo.Cita;
import edu.pe.cibertec.taller.modelo.EstadoCita;
import edu.pe.cibertec.taller.modelo.Mecanico;
import edu.pe.cibertec.taller.modelo.TipoServicio;
import edu.pe.cibertec.taller.repositorio.RepositorioCitas;
import edu.pe.cibertec.taller.repositorio.RepositorioMecanicos;
import edu.pe.cibertec.taller.servicio.impl.ServicioCitasImpl;
import edu.pe.cibertec.taller.util.ProveedorFechaHora;
import edu.pe.cibertec.taller.util.ServicioNotificaciones;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class GestionCitasSteps {

	private static final String NOMBRE_MECANICO = "Jason Dávila";
	private static final Long ID_OCUPADO = 1L;
	private static final Long ID_LIBRE = 2L;

	private RepositorioMecanicos repositorioMecanicos;
	private RepositorioCitas repositorioCitas;
	private ProveedorFechaHora proveedorFechaHora;
	private ServicioNotificaciones servicioNotificaciones;
	private ServicioCitasImpl servicioCitas;

	private Cita citaResultado;
	private RuntimeException excepcionCapturada;

	@Before
	public void inicializar() {
		repositorioMecanicos = mock(RepositorioMecanicos.class);
		repositorioCitas = mock(RepositorioCitas.class);
		proveedorFechaHora = mock(ProveedorFechaHora.class);
		servicioNotificaciones = mock(ServicioNotificaciones.class);
		servicioCitas = new ServicioCitasImpl(repositorioMecanicos, repositorioCitas,
				proveedorFechaHora, servicioNotificaciones);
		// El servicio retorna lo que guarda: el mock devuelve la misma cita recibida
		when(repositorioCitas.save(any(Cita.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
	}

	@Given("el reloj del taller marca el 09 de setiembre de 2026 a las 08:00")
	public void elRelojDelTallerMarca() {
		when(proveedorFechaHora.ahora()).thenReturn(LocalDateTime.of(2026, 9, 9, 8, 0));
	}

	@Given("un mecanico ocupado con una cita de MANTENIMIENTO_LIGERO de 10:00 a 12:00 el 10 de setiembre de 2026")
	public void unMecanicoOcupado() {
		Mecanico mecanicoOcupado = new Mecanico(ID_OCUPADO, NOMBRE_MECANICO, TipoServicio.MANTENIMIENTO_LIGERO);
		Cita citaZafiro = new Cita();
		citaZafiro.setId(100L);
		citaZafiro.setMecanico(mecanicoOcupado);
		citaZafiro.setPlacaVehiculo("DAV-540");
		citaZafiro.setTipoServicio(TipoServicio.MANTENIMIENTO_LIGERO);
		citaZafiro.setFechaHoraInicio(LocalDateTime.of(2026, 9, 10, 10, 0));
		citaZafiro.setDuracionHoras(2);
		citaZafiro.setEstado(EstadoCita.PROGRAMADA);
		when(repositorioMecanicos.findById(ID_OCUPADO)).thenReturn(Optional.of(mecanicoOcupado));
		when(repositorioCitas.findByMecanicoIdAndEstado(ID_OCUPADO, EstadoCita.PROGRAMADA))
				.thenReturn(List.of(citaZafiro));
	}

	@Given("otro mecanico libre con especialidad MANTENIMIENTO_LIGERO")
	public void otroMecanicoLibre() {
		Mecanico mecanicoLibre = new Mecanico(ID_LIBRE, NOMBRE_MECANICO, TipoServicio.MANTENIMIENTO_LIGERO);
		when(repositorioMecanicos.findById(ID_LIBRE)).thenReturn(Optional.of(mecanicoLibre));
		when(repositorioCitas.findByMecanicoIdAndEstado(ID_LIBRE, EstadoCita.PROGRAMADA))
				.thenReturn(Collections.emptyList());
	}

	@When("se agenda un MANTENIMIENTO_LIGERO para la placa {string} con el otro mecanico a las {int}:00")
	public void seAgendaConOtroMecanico(String placa, int hora) {
		agendar(ID_LIBRE, placa, hora);
	}

	@When("se agenda un MANTENIMIENTO_LIGERO para la placa {string} con el mecanico ocupado a las {int}:00")
	public void seAgendaConMecanicoOcupado(String placa, int hora) {
		agendar(ID_OCUPADO, placa, hora);
	}

	@Then("la cita queda registrada en estado PROGRAMADA")
	public void laCitaQuedaProgramada() {
		assertEquals(EstadoCita.PROGRAMADA, citaResultado.getEstado());
	}

	@Then("se notifica el agendamiento de la cita")
	public void seNotificaElAgendamiento() {
		verify(servicioNotificaciones, times(1)).notificarCitaAgendada(citaResultado);
	}

	@Then("el agendamiento se rechaza por horario ocupado")
	public void seRechazaPorHorarioOcupado() {
		assertTrue(excepcionCapturada instanceof HorarioOcupadoException);
	}

	private void agendar(Long idMecanico, String placa, int hora) {
		try {
			citaResultado = servicioCitas.agendarCita(idMecanico, placa,
					TipoServicio.MANTENIMIENTO_LIGERO, LocalDateTime.of(2026, 9, 10, hora, 0));
		} catch (RuntimeException excepcion) {
			excepcionCapturada = excepcion;
		}
	}
}
