package com.testcontainer.restartedContainer;

import com.github.javafaker.Faker;
import com.testcontainer.api.Customer;
import com.testcontainer.api.ICustomerService;
import io.restassured.http.ContentType;
import io.restassured.module.webtestclient.RestAssuredWebTestClient;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.blockhound.BlockingOperationError;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.testcontainer.databuilder.CustomerBuilder.customerWithIdAndName;
import static com.testcontainer.databuilder.CustomerBuilder.customerWithName;
import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.containsStringIgnoringCase;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpStatus.*;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
public class ControllerTests extends RestartedContainerConfig {

    private List<Customer> customerList;
    private Customer customerWithId;

    //MOCKED-SERVER: WEB-TEST-CLIENT(non-blocking client)'
    //SHOULD BE USED WITH 'TEST-CONTAINERS'
    //BECAUSE THERE IS NO 'REAL-SERVER' CREATED VIA DOCKER
    @Autowired
    WebTestClient mockedWebClient;

    @Autowired
    private ICustomerService service;

    //    final ContentType CONT_ANY = ContentType.ANY;
    //    final ContentType CONT_JSON = ContentType.JSON;
    final String REQ_MAP = "/customer";


    @BeforeAll
    static void beforeAll() {
        ConfigTests.beforeAll();
    }


    @AfterAll
    static void afterAll() {
        ConfigTests.afterAll();
    }


    @BeforeEach
    public void setUpLocal() {
        //REAL-SERVER INJECTED IN WEB-TEST-CLIENT(non-blocking client)'
        //SHOULD BE USED WHEN 'DOCKER-COMPOSE' UP A REAL-WEB-SERVER
        //BECAUSE THERE IS 'REAL-SERVER' CREATED VIA DOCKER-COMPOSE
        // realWebClient = WebTestClient.bindToServer()
        //                      .baseUrl("http://localhost:8080/customer")
        //                      .build();

        customerWithId = customerWithIdAndName(Faker.instance()
                                                    .idNumber()
                                                    .valid()).create();

        customerList = asList(
                customerWithName().create(),
                customerWithId
                             );
    }


    private void cleanDbToTest() {
        StepVerifier
                .create(service.deleteAll())
                .expectSubscription()
                .verifyComplete();

        System.out.println("\n\n==================> CLEAN-DB-TO-TEST" +
                                   " <==================\n\n");
    }


    private void StepVerifierCountCostumerFlux(Flux<Customer> flux,int totalElements) {
        StepVerifier
                .create(flux)
                .expectSubscription()
                .expectNextCount(totalElements)
                .verifyComplete();
    }


    @Test
    public static void checkContainer() {
        assertTrue(container.isRunning());
    }


    @Test
    public void save_WebTestClient() {

        mockedWebClient
                .post()
                .uri(REQ_MAP)
                .body(Mono.just(customerWithId),Customer.class)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(customerWithId.getId())
                .jsonPath("$.email")
                .isEqualTo(customerWithId.getEmail())
                .jsonPath("$.rating")
                .isEqualTo(customerWithId.getRating())
        ;
    }


    @Test
    public void save_RA() {
        cleanDbToTest();

        RestAssuredWebTestClient
                .given()
                .webTestClient(mockedWebClient)
                .header("Accept",ContentType.ANY)
                .header("Content-type",ContentType.JSON)
                .body(customerWithId)

                .when()
                .post(REQ_MAP)

                .then()
                .statusCode(CREATED.value())
                .and()
                .body("id",containsStringIgnoringCase(customerWithId.getId()))
                .and()
                .body("email",containsStringIgnoringCase(customerWithId.getEmail()))
                .and()
                .body("rating",is(customerWithId.getRating()))
        ;

        StepVerifierCountCostumerFlux(service.findAll(),1);
    }


    @Test
    public void find() {

        final Flux<Customer> customerFlux =
                service.deleteAll()
                       .thenMany(Flux.fromIterable(customerList))
                       .flatMap(service::save)
                       .doOnNext(item -> service.findAll());

        StepVerifierCountCostumerFlux(customerFlux,2);

        RestAssuredWebTestClient
                .given()
                .webTestClient(mockedWebClient)

                .when()
                .get(REQ_MAP)

                .then()
                .statusCode(OK.value())
                .log()
                .headers()
                .and()
                .log()
                .body()
                .and()

                .body("size()",is(2))
                .and()
                .body("id",hasItem(customerWithId.getId()))
        ;
    }


    @Test
    public void deleteAll() {
        cleanDbToTest();

        StepVerifier
                .create(service.save(customerWithId))
                .expectSubscription()
                .expectNext(customerWithId)
                .verifyComplete();

        StepVerifierCountCostumerFlux(service.findAll(),1);

        RestAssuredWebTestClient
                .given()
                .webTestClient(mockedWebClient)

                .when()
                .delete(REQ_MAP)

                .then()
                .statusCode(NO_CONTENT.value())
        ;

        StepVerifierCountCostumerFlux(service.findAll(),0);
    }


    @Test
    public void bHWorks() {
        try {
            FutureTask<?> task = new FutureTask<>(() -> {
                Thread.sleep(0);
                return "";
            });

            Schedulers.parallel()
                      .schedule(task);

            task.get(10,TimeUnit.SECONDS);
            Assertions.fail("should fail");
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            Assertions.assertTrue(e.getCause() instanceof BlockingOperationError,"detected");
        }
    }
}
