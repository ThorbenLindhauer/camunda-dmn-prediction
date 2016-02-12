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

import org.camunda.bpm.engine.repository.DecisionDefinition;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.slacktime.processengine.EngineDecisionRepository;
import org.camunda.bpm.slacktime.processengine.HistoryDistributionSource;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * @author Thorben Lindhauer
 *
 */
public class DmnPredictionTest {

  @Rule
  public ProcessEngineRule engineRule = new ProcessEngineRule();

  protected PredictiveDmnEngine predictionEngine;

  @Before
  public void setUp() {
    DecisionRepository decisionRepository = new EngineDecisionRepository(engineRule.getProcessEngine());
    DistributionSource distributionSource = new HistoryDistributionSource(engineRule.getProcessEngine());

    predictionEngine = new PredictiveDmnEngine(decisionRepository, distributionSource);
  }

  @Test
  @Deployment(resources = "table1.dmn")
  public void testRulePreditictionWithIncompleteInputs() {
    // given

    DecisionDefinition decisionDefinition = engineRule.getRepositoryService().createDecisionDefinitionQuery().singleResult();
    String decisionId = decisionDefinition.getId();

    evaluateDecisionTable(decisionId, "Winter", "no");
    evaluateDecisionTable(decisionId, "Winter", "no");
    evaluateDecisionTable(decisionId, "Winter", "yes");
    evaluateDecisionTable(decisionId, "Winter", "yes");
    evaluateDecisionTable(decisionId, "Winter", "yes");
    evaluateDecisionTable(decisionId, "Winter", "yes");
    evaluateDecisionTable(decisionId, "Summer", "no");
    evaluateDecisionTable(decisionId, "Summer", "yes");
    evaluateDecisionTable(decisionId, "Summer", "yes");
    evaluateDecisionTable(decisionId, "Summer", "yes");

    Predictor predictor = predictionEngine.getPredictor(decisionId);

    // when
    Evidence evidence = new Evidence();
    evidence.submit("season", "\"Summer\"");

    Distribution ruleDistribution = predictor.getPosterior("$rule", evidence);

    double rule1Prob = ruleDistribution.getProbability("row-876493691-1");
    double rule2Prob = ruleDistribution.getProbability("row-876493691-2");
    double rule3Prob = ruleDistribution.getProbability("row-876493691-3");
    double rule4Prob = ruleDistribution.getProbability("row-876493691-4");

    Assert.assertTrue(rule1Prob > 0);
    Assert.assertTrue(rule2Prob == 0);
    Assert.assertTrue(rule3Prob > 0);
    Assert.assertTrue(rule4Prob == 0);
    Assert.assertTrue(rule1Prob > rule3Prob);
  }

  protected void evaluateDecisionTable(String decisionId, String season, String hungry) {
    engineRule.getDecisionService().evaluateDecisionTableById(decisionId,
        Variables.createVariables().putValue("season", season).putValue("hungry", hungry));
  }
}
