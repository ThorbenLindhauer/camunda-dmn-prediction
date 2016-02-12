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
package org.camunda.bpm.slacktime.cockpit;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import org.camunda.bpm.cockpit.Cockpit;
import org.camunda.bpm.cockpit.plugin.resource.AbstractCockpitPluginRootResource;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.slacktime.DecisionRepository;
import org.camunda.bpm.slacktime.Distribution;
import org.camunda.bpm.slacktime.DistributionSource;
import org.camunda.bpm.slacktime.Evidence;
import org.camunda.bpm.slacktime.PredictiveDmnEngine;
import org.camunda.bpm.slacktime.Predictor;
import org.camunda.bpm.slacktime.processengine.EngineDecisionRepository;
import org.camunda.bpm.slacktime.processengine.HistoryDistributionSource;
import org.camunda.bpm.slacktime.processengine.ProcessInstanceEvidenceGenerator;

/**
 * @author Thorben Lindhauer
 *
 */
@Path("plugin/" + DecisionPredictionCockpitPlugin.ID)
public class DecisionPredictionPluginRootResource extends AbstractCockpitPluginRootResource {

  public DecisionPredictionPluginRootResource() {
    super(DecisionPredictionCockpitPlugin.ID);
  }

  @Path("{engine}/decision/{decisionId}/predict")
  @Produces("application/json")
  @GET
  public DistributionDto predictDecisionVariable(
      @PathParam("engine") String processEngineName,
      @PathParam("decisionId") final String decisionId,
      @QueryParam("processInstanceId") final String processInstanceId,
      @QueryParam("variable") final String variable) {

    final ProcessEngine processEngine = Cockpit.getRuntimeDelegate().getProcessEngine(processEngineName);
    ProcessEngineConfigurationImpl engineConfiguration = (ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration();

    final ProcessInstanceEvidenceGenerator evidenceGenerator = new ProcessInstanceEvidenceGenerator(processEngine);

    Distribution distribution = engineConfiguration.getCommandExecutorTxRequired().execute(new Command<Distribution>() {

      @Override
      public Distribution execute(CommandContext commandContext) {
        Evidence evidence = evidenceGenerator.generateEvidence(decisionId, processInstanceId);

        DecisionRepository decisionRepository = new EngineDecisionRepository(processEngine);
        DistributionSource distributionSource = new HistoryDistributionSource(processEngine);

        PredictiveDmnEngine predictionEngine = new PredictiveDmnEngine(decisionRepository, distributionSource);

        Predictor predictor = predictionEngine.getPredictor(decisionId);

        String evaluatingVariable = variable;
        if (evaluatingVariable == null) {
          evaluatingVariable = "$rule";
        }

        return predictor.getPosterior(evaluatingVariable, evidence);
      }

    });

    return DistributionDto.fromDistribution(variable, distribution);
  }

}
