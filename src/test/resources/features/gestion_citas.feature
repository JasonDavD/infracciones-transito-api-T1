Feature: Gestion de citas del taller mecanico

  Background:
    Given el reloj del taller marca el 09 de setiembre de 2026 a las 08:00
    And un mecanico ocupado con una cita de MANTENIMIENTO_LIGERO de 10:00 a 12:00 el 10 de setiembre de 2026

  Scenario: Registro exitoso de un mantenimiento ligero con otro mecanico
    Given otro mecanico libre con especialidad MANTENIMIENTO_LIGERO
    When se agenda un MANTENIMIENTO_LIGERO para la placa "DAV-540" con el otro mecanico a las 10:00
    Then la cita queda registrada en estado PROGRAMADA
    And se notifica el agendamiento de la cita

  Scenario: Rechazo por horario ocupado iniciando a las 11:00
    When se agenda un MANTENIMIENTO_LIGERO para la placa "DAV-540" con el mecanico ocupado a las 11:00
    Then el agendamiento se rechaza por horario ocupado

  Scenario: Registro contiguo iniciando a las 12:00 justo cuando termina la cita previa
    When se agenda un MANTENIMIENTO_LIGERO para la placa "DAV-540" con el mecanico ocupado a las 12:00
    Then la cita queda registrada en estado PROGRAMADA
    And se notifica el agendamiento de la cita
