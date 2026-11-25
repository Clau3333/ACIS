package cl.acis.microservicio.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.acis.microservicio.repository.WorkOrderRepository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderControllerIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private WorkOrderRepository repository;
    @Autowired private ObjectMapper mapper;

    @BeforeEach
    void clearIsolatedInMemoryDatabase() {
        repository.deleteAll();
    }

    @Test
    void createsListsAndReadsOrderWithoutPhotoPaths() throws Exception {
        MvcResult result = mvc.perform(post("/api/ordenes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().toString()))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.stage").value("ASSIGNED"))
                .andExpect(jsonPath("$.evidenceCount").value(0))
                .andExpect(jsonPath("$.photo").doesNotExist())
                .andReturn();

        long id = mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(get("/api/ordenes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/ordenes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.site").value("Local ACIS"));
        mvc.perform(get("/api/ordenes/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void validatesCreationAndRejectsPrivatePhotoPathFields() throws Exception {
        ObjectNode invalid = validRequest();
        invalid.put("site", " ");
        mvc.perform(post("/api/ordenes").contentType(MediaType.APPLICATION_JSON).content(invalid.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.site").exists());

        ObjectNode withPrivatePath = validRequest();
        withPrivatePath.put("photo", "file:///private/motorola/foto.jpg");
        mvc.perform(post("/api/ordenes").contentType(MediaType.APPLICATION_JSON).content(withPrivatePath.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatesFieldsAndEnforcesProgressionAndCustomerConfirmation() throws Exception {
        long id = createOrder();

        mvc.perform(put("/api/ordenes/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(requestAt("ACTIVE").toString()))
                .andExpect(status().isConflict());

        mvc.perform(put("/api/ordenes/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(requestAt("ACCEPTED").toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("ACCEPTED"));
        mvc.perform(put("/api/ordenes/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(requestAt("ACTIVE").toString()))
                .andExpect(status().isOk());

        ObjectNode done = requestAt("DONE");
        done.put("diagnosis", "Causa determinada");
        done.put("work", "Trabajo realizado");
        done.put("evidenceCount", 1);
        mvc.perform(put("/api/ordenes/{id}", id).contentType(MediaType.APPLICATION_JSON).content(done.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("DONE"));

        ObjectNode prematureClose = requestAt("CLOSED");
        prematureClose.put("diagnosis", "Causa determinada");
        prematureClose.put("work", "Trabajo realizado");
        prematureClose.put("evidenceCount", 1);
        prematureClose.put("customer", "Ana");
        prematureClose.put("customerConfirmed", true);
        mvc.perform(put("/api/ordenes/{id}", id).contentType(MediaType.APPLICATION_JSON).content(prematureClose.toString()))
                .andExpect(status().isConflict());

        ObjectNode confirmation = requestAt("DONE");
        confirmation.put("diagnosis", "Causa determinada");
        confirmation.put("work", "Trabajo realizado");
        confirmation.put("evidenceCount", 1);
        confirmation.put("customer", "Ana");
        confirmation.put("customerConfirmed", true);
        mvc.perform(put("/api/ordenes/{id}", id).contentType(MediaType.APPLICATION_JSON).content(confirmation.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerConfirmed").value(true));

        ObjectNode close = requestAt("CLOSED");
        close.put("diagnosis", "Causa determinada");
        close.put("work", "Trabajo realizado");
        close.put("evidenceCount", 1);
        close.put("customer", "Ana");
        close.put("customerConfirmed", true);
        mvc.perform(put("/api/ordenes/{id}", id).contentType(MediaType.APPLICATION_JSON).content(close.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("CLOSED"));

        mvc.perform(put("/api/ordenes/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(close.toString()))
                .andExpect(status().isConflict());
    }

    private long createOrder() throws Exception {
        MvcResult result = mvc.perform(post("/api/ordenes")
                        .contentType(MediaType.APPLICATION_JSON).content(validRequest().toString()))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private ObjectNode validRequest() {
        ObjectNode body = mapper.createObjectNode();
        body.put("id", 0);
        body.put("site", "Local ACIS");
        body.put("address", "Av. Central 100");
        body.put("equipment", "Equipo 1");
        body.put("technician", "Técnico 1");
        body.put("priority", "Normal");
        body.put("type", "Preventiva");
        body.put("problem", "Mantención preventiva");
        body.put("stage", "ASSIGNED");
        return body;
    }

    private ObjectNode requestAt(String stage) {
        ObjectNode body = validRequest();
        body.remove("id");
        body.put("stage", stage);
        return body;
    }
}
