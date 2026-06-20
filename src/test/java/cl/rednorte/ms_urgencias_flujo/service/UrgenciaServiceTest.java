package cl.rednorte.ms_urgencias_flujo.service;

import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ICriterion;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UrgenciaServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private IGenericClient fhirClient;

    @InjectMocks
    private UrgenciaService urgenciaService;

    private Encounter mockEncounter;

    @BeforeEach
    void setUp() {
        mockEncounter = new Encounter();
        mockEncounter.setId("encuentro-123");
        mockEncounter.setStatus(Encounter.EncounterStatus.ARRIVED);
        mockEncounter.setSubject(new Reference("Patient?identifier=12345678-9"));
        CodeableConcept motivoCode = new CodeableConcept().setText("Dolor de cabeza");
        mockEncounter.addReasonCode(motivoCode);
    }

    @Test
    void registrarIngreso_Exitoso_DeberiaRetornarIdEncuentro() {
        MethodOutcome mockOutcome = mock(MethodOutcome.class);
        IIdType mockIdType = mock(IIdType.class);

        when(fhirClient.create().resource(any(Encounter.class)).execute()).thenReturn(mockOutcome);
        when(mockOutcome.getId()).thenReturn(mockIdType);
        when(mockIdType.getIdPart()).thenReturn("encuentro-123");

        String id = urgenciaService.registrarIngreso("12345678-9", "Dolor de cabeza");

        assertNotNull(id);
        assertEquals("encuentro-123", id);
    }

    @Test
    void procesarTriage_Exitoso_DeberiaEjecutarTransaccion() {
        when(fhirClient.read().resource(Encounter.class).withId("encuentro-123").execute()).thenReturn(mockEncounter);

        Bundle responseBundle = new Bundle();
        when(fhirClient.transaction().withBundle(any(Bundle.class)).execute()).thenReturn(responseBundle);

        Map<String, Object> datosTriage = new HashMap<>();
        datosTriage.put("categorizacion", "C1");
        datosTriage.put("presion", "120/80");

        urgenciaService.procesarTriage("encuentro-123", datosTriage);

        assertEquals(Encounter.EncounterStatus.TRIAGED, mockEncounter.getStatus());
        assertEquals("C1", mockEncounter.getPriority().getCodingFirstRep().getCode());
    }

    @Test
    void calcularTiempoEspera_DeberiaRetornar45Minutos() {
        Bundle mockBundle = new Bundle();
        when(fhirClient.search().forResource(any(Class.class)).where(any(ICriterion.class))
                .returnBundle(any(Class.class)).execute()).thenReturn(mockBundle);

        int minutos = urgenciaService.calcularTiempoEspera("12345678-9");

        assertEquals(45, minutos);
    }

    @Test
    void cancelarAtencion_Exitoso_DeberiaActualizarEncuentro() {
        when(fhirClient.read().resource(Encounter.class).withId("encuentro-123").execute()).thenReturn(mockEncounter);

        MethodOutcome mockOutcome = mock(MethodOutcome.class);
        when(fhirClient.update().resource(any(Encounter.class)).execute()).thenReturn(mockOutcome);

        urgenciaService.cancelarAtencion("encuentro-123", "9876543-2");

        assertEquals(Encounter.EncounterStatus.CANCELLED, mockEncounter.getStatus());
        assertTrue(mockEncounter.getExtension().get(0).getValue().toString().contains("9876543-2"));
    }

    @Test
    void obtenerFichaClinica_Exitoso_DeberiaRetornarDatosFicha() {
        when(fhirClient.read().resource(Encounter.class).withId("encuentro-123").execute()).thenReturn(mockEncounter);

        Map<String, Object> ficha = urgenciaService.obtenerFichaClinica("encuentro-123");

        assertNotNull(ficha);
        assertEquals("encuentro-123", ficha.get("idEncuentro"));
        assertEquals("Dolor de cabeza", ficha.get("motivo"));
        assertEquals("arrived", ficha.get("estadoActual"));
    }

    @Test
    void finalizarAtencion_Exitoso_DeberiaActualizarEncuentroAFinished() {
        when(fhirClient.read().resource(Encounter.class).withId("encuentro-123").execute()).thenReturn(mockEncounter);

        MethodOutcome mockOutcome = mock(MethodOutcome.class);
        when(fhirClient.update().resource(any(Encounter.class)).execute()).thenReturn(mockOutcome);

        urgenciaService.finalizarAtencion("encuentro-123", "Migraña severa");

        assertEquals(Encounter.EncounterStatus.FINISHED, mockEncounter.getStatus());
        assertEquals("Migraña severa", mockEncounter.getDiagnosisFirstRep().getCondition().getDisplay());
    }
}
