/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.slacktime;

import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Thorben Lindhauer
 *
 */
public class StandaloneDmnPredictionTest {

  protected static final double TOLERABLE_ASSERT_DELTA = 0.00001d;

  @Test
  public void testPreditictionWithIncompleteInputs() {
    // TODO: Engine muss irgendwoher die Verteilungen der Inputs kennen

    String decisionDefinitionId = "table1";

    MockDistributionSource distributionSource = new MockDistributionSource();

    MockDistribution hungryDistribution = new MockDistribution();
    hungryDistribution.setProbability("\"yes\"", 0.3d);
    hungryDistribution.setProbability("\"no\"", 0.7d);

    MockDistribution seasonDistribution = new MockDistribution();
    seasonDistribution.setProbability("\"Winter\"", 0.5d);
    seasonDistribution.setProbability("\"Summer\"", 0.5d);

    distributionSource.addDistribution("season", seasonDistribution);
    distributionSource.addDistribution("hungry", hungryDistribution);

    MockDecisionRepository repository = new MockDecisionRepository();
    repository.addDecisionModel(decisionDefinitionId, readModelFromClasspath("table1.dmn"));

    PredictiveDmnEngine engine = new PredictiveDmnEngine(repository, distributionSource);


    // auf dem Predictor-Objekt kann man dann beliebige Queries machen,
    // das heißt getPredictor initialisiert die Verteilungen
    Predictor predictor = engine.getPredictor(decisionDefinitionId);

    Evidence evidence = new Evidence();
    evidence.submit("season", "\"Winter\"");
    Distribution ruleDistribution = predictor.getPosterior("$rule", evidence);
    Assert.assertEquals(0.0d, ruleDistribution.getProbability("row-876493691-1"), TOLERABLE_ASSERT_DELTA);
    Assert.assertEquals(0.7d, ruleDistribution.getProbability("row-876493691-2"), TOLERABLE_ASSERT_DELTA);
    Assert.assertEquals(0.0d, ruleDistribution.getProbability("row-876493691-3"), TOLERABLE_ASSERT_DELTA);
    Assert.assertEquals(0.3d, ruleDistribution.getProbability("row-876493691-4"), TOLERABLE_ASSERT_DELTA);


    // TODO: und hier dann irgendwas, dass man dem Predictor eine MEnge von
    // Input- und Outputwerten geben kann und man dann eine Verteilung über die fehlenden
    // Variablen erhält

    // vll kann man das auch so modellieren: {inputs} => RULE => {outputs} und RULE => {outputs} spart man sich erstmal
  }

  protected DmnModelInstance readModelFromClasspath(String resource) {
    return Dmn.readModelFromStream(StandaloneDmnPredictionTest.class.getClassLoader().getResourceAsStream(resource));
  }
}
