package at.tuwien.infosys;

import at.tuwien.infosys.datasources.ScalingActivityRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
@TestPropertySource(locations="classpath:application.properties")
public class EvaluationTests {

    @Autowired
    private ScalingActivityRepository sar;


    @Before
    public void setup() {
    }

    @Test
    public void generate() {

        //ScalingActivity sa = sar.findFirstOrderByTime().get(0);





    }




}
