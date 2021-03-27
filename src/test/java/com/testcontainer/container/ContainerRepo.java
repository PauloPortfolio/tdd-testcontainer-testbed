package com.testcontainer.container;

import com.testcontainer.api.Customer;
import com.testcontainer.api.ICustomerRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.blockhound.BlockingOperationError;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.testcontainer.databuilder.CustomerBuilder.customerWithName;
import static com.testcontainer.databuilder.CustomerBuilder.customerWithNameButEmailIsNull;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;

//TUTORIAL: https://rieckpil.de/mongodb-testcontainers-setup-for-datamongotest/
public class ContainerRepo extends ConfigTests {

    private Customer cust1, cust2, customerEmailNull;
    private List<Customer> customerList;

    @Autowired
    private ICustomerRepo repo;

    @BeforeEach
    void setUp() {
        cust1 = customerWithName().create();
        cust2 = customerWithName().create();
        customerEmailNull = customerWithNameButEmailIsNull().create();

        customerList = Arrays.asList(cust1,cust2);

        repo.deleteAll()
            .thenMany(Flux.fromIterable(customerList))
            .flatMap(repo::save)
            .doOnNext(item -> System.out.println(" Inserted item is: " + item))
            .blockLast(); // THATS THE WHY, BLOCKHOUND IS NOT BEING USED.
    }

    @AfterEach
    void tearDown() {
        repo.deleteAll();
    }

    @Test
    void checkContainer() {
        assertTrue(container.isRunning());
    }

    @Test
    public void trySaveInvalidObjectWithNullInEmail() {
        StepVerifier
                .create(repo.save(customerEmailNull))
                .expectSubscription()
                .expectNext(customerEmailNull)
                .verifyComplete();
    }

    @Test
    public void save() {
        StepVerifier
                .create(repo.save(cust1))
                .expectSubscription()
                .expectNext(cust1)
                .verifyComplete();
    }


    @Test
    public void findAll() {
        StepVerifier
                .create(repo.findAll())
                .expectSubscription()
                .expectNextCount(customerList.toArray().length)
                .verifyComplete();
    }


    @Test
    public void findAllNextMatches() {
        StepVerifier
                .create(repo.findAll())
                .expectNextMatches(u -> u.getId()
                                         .equals(cust1.getId()))
                .expectComplete();
    }


    @Test
    public void findAllNext() {

        StepVerifier
                .create(repo.findAll())
                .expectNext(cust1)
                .expectNext(cust2)
                .expectComplete();
    }

    @Test
    public void deleteAll() {

        StepVerifier
                .create(repo.deleteAll())
                .expectSubscription()
                .verifyComplete();

        StepVerifier
                .create(repo.findAll())
                .expectSubscription()
                .expectNextCount(0)
                .verifyComplete();

    }


    @Test
    public void blockHoundWorks() {
        try {
            FutureTask<?> task = new FutureTask<>(() -> {
                Thread.sleep(0);
                return "";
            });

            Schedulers.parallel()
                      .schedule(task);

            task.get(10,TimeUnit.SECONDS);
            fail("should fail");
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            assertTrue(e.getCause() instanceof BlockingOperationError,"detected");
        }
    }
}