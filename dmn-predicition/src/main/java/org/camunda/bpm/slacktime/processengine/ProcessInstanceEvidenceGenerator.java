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
package org.camunda.bpm.slacktime.processengine;

import java.util.List;

import org.camunda.bpm.dmn.engine.DmnEngine;
import org.camunda.bpm.dmn.engine.impl.DefaultDmnEngineConfiguration;
import org.camunda.bpm.dmn.engine.impl.DmnDecisionTableInputImpl;
import org.camunda.bpm.dmn.engine.impl.DmnDecisionTableRuleImpl;
import org.camunda.bpm.dmn.engine.impl.DmnExpressionImpl;
import org.camunda.bpm.dmn.engine.impl.spi.el.ElExpression;
import org.camunda.bpm.dmn.engine.impl.spi.el.ElProvider;
import org.camunda.bpm.dmn.feel.impl.FeelEngine;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.dmn.entity.repository.DecisionDefinitionEntity;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.impl.VariableMapImpl;
import org.camunda.bpm.engine.variable.impl.context.SingleVariableContext;
import org.camunda.bpm.slacktime.Evidence;

/**
 * @author Thorben Lindhauer
 *
 */
public class ProcessInstanceEvidenceGenerator {

  protected ProcessEngine processEngine;

  public ProcessInstanceEvidenceGenerator(ProcessEngine engine) {
    this.processEngine = engine;
  }

  public Evidence generateEvidence(final String decisionDefinitionId, final String processInstanceId) {

    ProcessEngineConfigurationImpl engineConfiguration = (ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration();
    return engineConfiguration.getCommandExecutorTxRequired().execute(new Command<Evidence>() {

      public Evidence execute(CommandContext commandContext) {
        DecisionDefinitionEntity decisionDefinition =
            Context.getProcessEngineConfiguration().getDeploymentCache().findDeployedDecisionDefinitionById(decisionDefinitionId);
        ExecutionEntity processInstance = commandContext.getExecutionManager().findExecutionById(processInstanceId);

        DefaultDmnEngineConfiguration dmnEngineConfiguration = (DefaultDmnEngineConfiguration) Context
            .getProcessEngineConfiguration()
            .getDmnEngineConfiguration();

        VariableMapImpl variables = processInstance.getVariables();
        Evidence evidence = new Evidence();

        // TODO: would have to check the expression languages, etc.
        ElProvider elProvider = dmnEngineConfiguration.getElProvider();
        FeelEngine feelEngine = dmnEngineConfiguration.getFeelEngine();


        for (int i = 0; i < decisionDefinition.getInputs().size(); i++) {
          DmnDecisionTableInputImpl input = decisionDefinition.getInputs().get(i);
          DmnExpressionImpl inputExpression = input.getExpression();
          ElExpression expression = elProvider.createExpression("${" + inputExpression.getExpression() + "}"); // TODO: works for juel only

          Object value = null;
          try {
            value = expression.getValue(variables);
          } catch (Exception e) {
            // a failing evaluation is treated as if the input is not known
            continue;
          }

          SingleVariableContext inputContext = new SingleVariableContext(input.getInputVariable(), Variables.untypedValue(value));

          List<DmnDecisionTableRuleImpl> rules = decisionDefinition.getRules();
          for (DmnDecisionTableRuleImpl rule : rules) {
            String inputEntryExpression = rule.getConditions().get(i).getExpression();
            boolean testFulfilled = feelEngine.evaluateSimpleUnaryTests(inputEntryExpression, input.getInputVariable(), inputContext);
            if (testFulfilled) {
              evidence.submit(input.getName(), inputEntryExpression);
              break; // there should be no more than one input expression that is satisfied
            }
          }
        }

        return evidence;
      }
    });
  }
}
