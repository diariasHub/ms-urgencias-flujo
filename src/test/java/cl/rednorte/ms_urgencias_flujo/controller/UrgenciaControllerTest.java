package cl.rednorte.ms_urgencias_flujo.controller;

import cl.rednorte.ms_urgencias_flujo.service.UrgenciaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UrgenciaController.class)
@AutoConfigureMockMvc(addFilters = false) // Disable security for unit tests
public class UrgenciaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UrgenciaService urgenciaService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void ping_DeberiaRetornarOk() throws Exception {
        mockMvc.perform(get("/urgencias/ping"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void ingresoRecepcion_DeberiaRetornarMensajeExito() throws Exception {
        when(urgenciaService.registrarIngreso("12345678-9", "Dolor de cabeza")).thenReturn("encuentro-123");

        Map<String, String> payload = Map.of("rut", "12345678-9", "motivo", "Dolor de cabeza");

        mockMvc.perform(post("/urgencias/ingreso")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(content().string("Paciente ingresado. ID Encuentro: encuentro-123"));

        verify(urgenciaService).registrarIngreso("12345678-9", "Dolor de cabeza");
    }

    @Test
    void realizarTriage_DeberiaRetornarOk() throws Exception {
        doNothing().when(urgenciaService).procesarTriage(eq("encuentro-123"), any());

        Map<String, Object> datosTriage = Map.of("categorizacion", "C1", "presion", "120/80");

        mockMvc.perform(put("/urgencias/triage/encuentro-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(datosTriage)))
                .andExpect(status().isOk())
                .andExpect(content().string("Triage completado con éxito."));

        verify(urgenciaService).procesarTriage(eq("encuentro-123"), any());
    }

    @Test
    void consultarEspera_DeberiaRetornarTiempoEspera() throws Exception {
        when(urgenciaService.calcularTiempoEspera("12345678-9")).thenReturn(45);

        mockMvc.perform(get("/urgencias/espera/12345678-9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rut").value("12345678-9"))
                .andExpect(jsonPath("$.tiempoEsperaMinutos").value(45));

        verify(urgenciaService).calcularTiempoEspera("12345678-9");
    }

    @Test
    void rechazarAtencion_DeberiaRetornarOk() throws Exception {
        doNothing().when(urgenciaService).cancelarAtencion("encuentro-123", "12345678-9");

        Map<String, String> payload = Map.of("idEncuentro", "encuentro-123", "rutConfirmacion", "12345678-9");

        mockMvc.perform(put("/urgencias/rechazo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(content().string("Atención rechazada por el usuario de forma conforme."));

        verify(urgenciaService).cancelarAtencion("encuentro-123", "12345678-9");
    }

    @Test
    void verFichaMedica_DeberiaRetornarDatosFicha() throws Exception {
        Map<String, Object> mockFicha = Map.of("idEncuentro", "encuentro-123", "motivo", "Dolor de cabeza", "estadoActual", "arrived");
        when(urgenciaService.obtenerFichaClinica("encuentro-123")).thenReturn(mockFicha);

        mockMvc.perform(get("/urgencias/ficha/encuentro-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idEncuentro").value("encuentro-123"))
                .andExpect(jsonPath("$.motivo").value("Dolor de cabeza"))
                .andExpect(jsonPath("$.estadoActual").value("arrived"));

        verify(urgenciaService).obtenerFichaClinica("encuentro-123");
    }

    @Test
    void darAltaMedica_DeberiaRetornarOk() throws Exception {
        doNothing().when(urgenciaService).finalizarAtencion("encuentro-123", "Migraña");

        Map<String, String> payload = Map.of("diagnostico", "Migraña");

        mockMvc.perform(put("/urgencias/alta/encuentro-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(content().string("Alta médica procesada correctamente."));

        verify(urgenciaService).finalizarAtencion("encuentro-123", "Migraña");
    }
}
