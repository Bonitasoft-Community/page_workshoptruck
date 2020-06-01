package org.bonitasoft.workshoptruck.design;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import org.bonitasoft.engine.api.PageAPI;
import org.bonitasoft.engine.api.ProcessAPI;
import org.bonitasoft.engine.bpm.actor.ActorCriterion;
import org.bonitasoft.engine.bpm.actor.ActorDefinition;
import org.bonitasoft.engine.bpm.actor.ActorInstance;
import org.bonitasoft.engine.bpm.actor.ActorMember;
import org.bonitasoft.engine.bpm.bar.BusinessArchiveBuilder;
import org.bonitasoft.engine.bpm.bar.actorMapping.ActorMapping;
import org.bonitasoft.engine.bpm.connector.ConnectorDefinition;
import org.bonitasoft.engine.bpm.contract.ContractDefinition;
import org.bonitasoft.engine.bpm.contract.InputDefinition;
import org.bonitasoft.engine.bpm.flownode.ActivityDefinition;
import org.bonitasoft.engine.bpm.flownode.AutomaticTaskDefinition;
import org.bonitasoft.engine.bpm.flownode.EndEventDefinition;
import org.bonitasoft.engine.bpm.flownode.FlowElementContainerDefinition;
import org.bonitasoft.engine.bpm.flownode.GatewayDefinition;
import org.bonitasoft.engine.bpm.flownode.GatewayType;
import org.bonitasoft.engine.bpm.flownode.IntermediateCatchEventDefinition;
import org.bonitasoft.engine.bpm.flownode.StartEventDefinition;
import org.bonitasoft.engine.bpm.flownode.TransitionDefinition;
import org.bonitasoft.engine.bpm.flownode.UserTaskDefinition;
import org.bonitasoft.engine.bpm.form.FormMappingModelBuilder;
import org.bonitasoft.engine.bpm.parameter.ParameterDefinition;
import org.bonitasoft.engine.bpm.process.DesignProcessDefinition;
import org.bonitasoft.engine.bpm.process.InvalidProcessDefinitionException;
import org.bonitasoft.engine.bpm.process.ProcessDefinition;
import org.bonitasoft.engine.bpm.process.ProcessDefinitionNotFoundException;
import org.bonitasoft.engine.bpm.process.ProcessDeployException;
import org.bonitasoft.engine.bpm.process.ProcessEnablementException;
import org.bonitasoft.engine.bpm.process.impl.ActivityDefinitionBuilder;
import org.bonitasoft.engine.bpm.process.impl.ContractDefinitionBuilder;
import org.bonitasoft.engine.bpm.process.impl.ContractInputDefinitionBuilder;
import org.bonitasoft.engine.bpm.process.impl.InputContainerDefinitionBuilder;
import org.bonitasoft.engine.bpm.process.impl.ProcessDefinitionBuilder;
import org.bonitasoft.engine.bpm.process.impl.UserTaskDefinitionBuilder;
import org.bonitasoft.engine.exception.AlreadyExistsException;
import org.bonitasoft.engine.form.FormMapping;
import org.bonitasoft.engine.form.FormMappingSearchDescriptor;
import org.bonitasoft.engine.form.FormMappingType;
import org.bonitasoft.engine.operation.Operation;
import org.bonitasoft.engine.page.Page;
import org.bonitasoft.engine.search.SearchOptionsBuilder;
import org.bonitasoft.engine.search.SearchResult;
import org.bonitasoft.log.event.BEvent;
import org.bonitasoft.log.event.BEvent.Level;
import org.bonitasoft.log.event.BEventFactory;

public class ProcessDesignBuilder {

    static Logger logger = Logger.getLogger(ProcessDesignBuilder.class.getName());

    private static BEvent eventDesignProcess = new BEvent(ProcessDesignBuilder.class.getName(), 1, Level.ERROR,
            "Error duplicate the  process", "", "", "Check Exception");

    private static BEvent eventCantReproduceThisTask = new BEvent(ProcessDesignBuilder.class.getName(), 2, Level.ERROR,
            "Can't reproduce this task", "Duplicate tool can't handle this tasks", "Process is not duplicated", "Use an another process");

    private static BEvent eventProcessGeneration = new BEvent(ProcessDesignBuilder.class.getName(), 3, Level.ERROR,
            "Error while generating the process", "Error during the generation of the process", "Process is not duplicated", "Use an another process");
    private static BEvent eventProcessEnable = new BEvent(ProcessDesignBuilder.class.getName(), 4, Level.ERROR,
            "Error while enabling the process", "Error during the enabling of the process. Something is missing on the definition", "Process is not enable", "Check administration");
    private static BEvent eventGenerationError = new BEvent(ProcessDesignBuilder.class.getName(), 5, Level.APPLICATIONERROR,
            "Process does not exist", "Process does not exist a done() must be executed with success before", "Process does not exist", "Check the generation");
    private static BEvent eventCantFindActor = new BEvent(ProcessDesignBuilder.class.getName(), 6, Level.APPLICATIONERROR,
            "CantFindActor", "Actor can't be find. To duplicate the actor mapping, all actors must be retrieved in the reference process", "Actor mapping is not finish completely", "Check the generation");

    ProcessDesign processDesign;
    ProcessAPI processAPI;
    PageAPI pageAPI;

    protected ProcessDesignBuilder(ProcessDesign processDesign) {
        this.processDesign = processDesign;
        this.processAPI = processDesign.processAPI;
        this.pageAPI = processDesign.pageAPI;
    }

    /**
     * @author Firstname Lastname
     */
    public enum TypeElement {
        TRANSITION, ACTIVITY
    }

    public static class CloneExclusion {

        Map<TypeElement, Set<Long>> mapCloneException = new HashMap<>();

        public void addElement(TypeElement typeElement, Long idArtefact) {
            Set<Long> setArtifact = mapCloneException.get(typeElement);
            if (setArtifact == null)
                setArtifact = new HashSet<>();
            setArtifact.add(idArtefact);
            mapCloneException.put(typeElement, setArtifact);
        }

        public boolean isExcluded(TypeElement typeElement, Long id) {
            if (mapCloneException.containsKey(typeElement))
                if (mapCloneException.get(typeElement).contains(id))
                    return true;
            return false;
        }
    }

    /* -------------------------------------------------------------------- */
    /*                                                                      */
    /* operation on the new process define */
    /*
     * The operation generates
     * - artifactBuilder.processDefinitionBuilder
     * -
     * in the done() operation, all theses artefact are compiled and give back a
     * newPocessDesign
     * /*
     */
    /* -------------------------------------------------------------------- */
    public static class ArtifactBuilder {

        ProcessDefinitionBuilder processDefinitionBuilder = null;
        FormMappingModelBuilder formMappingBuilder = null;
        ActorMapping actorMapping = null;

        ArtifactBuilder() {
            formMappingBuilder = FormMappingModelBuilder.buildFormMappingModel();

        }
    }

    public ArtifactBuilder artifactBuilder = new ArtifactBuilder();

    private List<BEvent> listEventsGeneration = null;

    /**
     * Clone the process. Generate a new artifactBuilder.processDefinitionBuilder
     * 
     * @param processName
     * @param processVersion
     * @param mapCloneException
     * @return
     */
    public List<BEvent> clone(String processName, String processVersion, CloneExclusion cloneExclusion) {
        StringBuilder messageConstruction = new StringBuilder();

        FlowElementContainerDefinition sourceContainer = processDesign.sourceDesign.getFlowElementContainer();
        listEventsGeneration = new ArrayList<>();
        try {
            artifactBuilder.processDefinitionBuilder = new ProcessDefinitionBuilder().createNewInstance(processName, processVersion);
            messageConstruction.append("artifactBuilder.processDefinitionBuilder = new artifactBuilder.processDefinitionBuilder().createNewInstance(\"" + processName + "\",\"" + processVersion + "\");");
            // Let's duplicate all artifacts
            for (ActorDefinition actor : processDesign.sourceDesign.getActorsList()) {
                artifactBuilder.processDefinitionBuilder.addActor(actor.getName(), actor.isInitiator());
                messageConstruction.append("artifactBuilder.processDefinitionBuilder.addActor(" + actor.getName() + "," + actor.isInitiator() + ");");
            }

            // value should be added after the creation
            for (ParameterDefinition parameter : processDesign.sourceDesign.getParameters()) {
                artifactBuilder.processDefinitionBuilder.addParameter(parameter.getName(), parameter.getType());
            }

            // contract
            ContractDefinition processContract = processAPI.getProcessContract(processDesign.processDefinitionId);
            ContractDefinitionBuilder processContractBuilder = artifactBuilder.processDefinitionBuilder.addContract();
            fullFillContract(processContractBuilder, processContract);

            for (StartEventDefinition startEvent : sourceContainer.getStartEvents()) {
                artifactBuilder.processDefinitionBuilder.addStartEvent(startEvent.getName());
            }
            for (GatewayDefinition gateway : sourceContainer.getGatewaysList()) {
                artifactBuilder.processDefinitionBuilder.addGateway(gateway.getName(), gateway.getGatewayType());
                messageConstruction.append("artifactBuilder.processDefinitionBuilder.addGateway(" + gateway.getName() + "," + gateway.getGatewayType() + ");");
            }
            for (IntermediateCatchEventDefinition catchEvent : sourceContainer.getIntermediateCatchEvents()) {
                artifactBuilder.processDefinitionBuilder.addIntermediateCatchEvent(catchEvent.getName());
            }

            for (ActivityDefinition activity : sourceContainer.getActivities()) {
                if (cloneExclusion.isExcluded(TypeElement.ACTIVITY, activity.getId()))
                    continue;
                addActivity(activity.getName(), activity);
            }

            for (EndEventDefinition endEvent : sourceContainer.getEndEvents()) {
                artifactBuilder.processDefinitionBuilder.addEndEvent(endEvent.getName());
            }

            // add all transition EXCEPT the one between the source and the destination
            for (TransitionDefinition transition : sourceContainer.getTransitions()) {
                if (cloneExclusion.isExcluded(TypeElement.TRANSITION, transition.getId()))
                    continue;
                artifactBuilder.processDefinitionBuilder.addTransition(transition.getSourceFlowNode().getName(),
                        transition.getTargetFlowNode().getName(),
                        transition.getCondition());
            }

            copyFormMapping();

            logger.fine("Construction:" + messageConstruction.toString());

        } catch (Exception e) {
            listEventsGeneration.add(new BEvent(eventDesignProcess, e, "process[" + processName + "]"));
        }
        return listEventsGeneration;

    }

    /**
     * fullfill recursivelly the contract
     * 
     * @param contractBuilder
     * @param contractDefinition
     */
    private void fullFillContract(ContractDefinitionBuilder contractBuilder, ContractDefinition contractDefinition) {
        fullFillContractOneLevel(contractBuilder, contractDefinition.getInputs());
    }

    private void fullFillContractOneLevel(InputContainerDefinitionBuilder contractBuilder, List<InputDefinition> inputs) {

        for (InputDefinition input : inputs) {
            if (input.getType() == null) {
                ContractInputDefinitionBuilder complexeDefinitionBuilder = contractBuilder.addComplexInput(input.getName(), input.getDescription(), input.isMultiple());
                fullFillContractOneLevel(complexeDefinitionBuilder, input.getInputs());
            } else
                contractBuilder.addInput(input.getName(), input.getType(), input.getDescription(), input.isMultiple());
        }
    }

    private void copyFormMapping() {
        try {

            SearchOptionsBuilder search = new SearchOptionsBuilder(0, 100);
            search.filter(FormMappingSearchDescriptor.PROCESS_DEFINITION_ID, processDesign.processDefinitionId);
            SearchResult<FormMapping> searchFormMapping = processAPI.searchFormMappings(search.done());

            for (FormMapping formMapping : searchFormMapping.getResult()) {
                Page page = pageAPI.getPage(formMapping.getPageId());
                if (page!=null)
                {
                if (formMapping.getType() == FormMappingType.PROCESS_START)
                    artifactBuilder.formMappingBuilder.addProcessStartForm(page.getName(), formMapping.getTarget());
                if (formMapping.getType() == FormMappingType.PROCESS_OVERVIEW)
                    artifactBuilder.formMappingBuilder.addProcessOverviewForm(page.getName(), formMapping.getTarget());
                artifactBuilder.formMappingBuilder.addTaskForm(page.getName(), formMapping.getTarget(), formMapping.getTask());
                }
            }

        } catch (Exception e) {
            listEventsGeneration.add(new BEvent(eventProcessGeneration, e, e.getMessage()));
        }

    }

    /**
     * 
     */
    public void addTransition(String source, String target) {
        artifactBuilder.processDefinitionBuilder.addTransition(source, target);
    }

    /**
     * @param taskName
     * @param activity
     * @return
     */
    public void addActivity(String taskName, ActivityDefinition activity) {

        ActivityDefinitionBuilder activityDefinitionBuilder;
        if (activity instanceof UserTaskDefinition) {
            UserTaskDefinition humanTask = (UserTaskDefinition) activity;
            UserTaskDefinitionBuilder userTaskDefinitionBuilder = artifactBuilder.processDefinitionBuilder.addUserTask(taskName, humanTask.getActorName());
            // How to attach the form ?
            activityDefinitionBuilder = userTaskDefinitionBuilder;
            ContractDefinition contractDefinition = humanTask.getContract();
            ContractDefinitionBuilder contractBuilder = userTaskDefinitionBuilder.addContract();
            fullFillContract(contractBuilder, contractDefinition);

        }

        else if (activity instanceof AutomaticTaskDefinition) {
            activityDefinitionBuilder = artifactBuilder.processDefinitionBuilder.addAutomaticTask(taskName);
        } else {
            listEventsGeneration.add(new BEvent(eventCantReproduceThisTask, "Task[" + activity.getName() + "] type[" + activity.getClass().getName() + "]"));
            return;
        }

        for (Operation operation : activity.getOperations()) {
            activityDefinitionBuilder.addOperation(operation);
        }

        for (ConnectorDefinition connector : activity.getConnectors()) {
            activityDefinitionBuilder.addConnector(connector.getName(), connector.getConnectorId(), connector.getVersion(), connector.getActivationEvent());
        }

    }

    public void associateForm(String taskName, ActivityDefinition modelActivity) {
        try {

            SearchOptionsBuilder search = new SearchOptionsBuilder(0, 100);
            search.filter(FormMappingSearchDescriptor.PROCESS_DEFINITION_ID, processDesign.processDefinitionId);
            SearchResult<FormMapping> searchFormMapping = processAPI.searchFormMappings(search.done());

            // formMappingBuilder.addProcessStartForm(startForm, FormMappingTarget.INTERNAL);
            // formMappingBuilder.addProcessOverviewForm(overviewForm, FormMappingTarget.INTERNAL);
            for (FormMapping formMapping : searchFormMapping.getResult()) {
                if (modelActivity.getName().equals(formMapping.getTask())) {
                    Page page = pageAPI.getPage(formMapping.getPageId());
                    if (page!=null)
                        artifactBuilder.formMappingBuilder.addTaskForm(page.getName(), formMapping.getTarget(), taskName);
                }
            }
        } catch (Exception e) {
            listEventsGeneration.add(new BEvent(eventProcessGeneration, e, e.getMessage()));
        }

    }
    /* -------------------------------------------------------------------- */
    /*                                                                      */
    /* getStatus */
    /*                                                                      */
    /* -------------------------------------------------------------------- */

    public boolean isGenerationOk() {
        return !BEventFactory.isError(listEventsGeneration);
    }

    public List<BEvent> getListEventsGeneration() {
        return listEventsGeneration;
    }

    /* -------------------------------------------------------------------- */
    /*                                                                      */
    /* Do the done */
    /*                                                                      */
    /* -------------------------------------------------------------------- */
    DesignProcessDefinition newDesign;
    // after the deployment the newProcessDefinition will be available
    ProcessDefinition newProcessDefinition;

    public List<BEvent> done() {
        if (!isGenerationOk())
            return listEventsGeneration;
        try {
            BusinessArchiveBuilder businessArchiveBuilder = new BusinessArchiveBuilder().createNewBusinessArchive();
            if (artifactBuilder.processDefinitionBuilder != null)
                businessArchiveBuilder.setProcessDefinition(artifactBuilder.processDefinitionBuilder.done());
            if (artifactBuilder.formMappingBuilder != null) {
                businessArchiveBuilder.setFormMappings(artifactBuilder.formMappingBuilder.build());
            }
            if (artifactBuilder.actorMapping != null)
                businessArchiveBuilder.setActorMapping(artifactBuilder.actorMapping);
            newDesign = artifactBuilder.processDefinitionBuilder.done();
        } catch (InvalidProcessDefinitionException e) {
            listEventsGeneration.add(new BEvent(eventProcessGeneration, e, e.getMessage()));
        }
        return listEventsGeneration;
    }

    /* -------------------------------------------------------------------- */
    /*                                                                      */
    /* Operation on new process */
    /*                                                                      */
    /* -------------------------------------------------------------------- */

    /**
     * After the deploy, it's not possible to works on the design
     * 
     * @return
     */
    public List<BEvent> deploy() {
        List<BEvent> listEvents = new ArrayList<>();
        try {
            // contract violation : method done must be called before
            if (newDesign == null) {
                listEvents.add(eventGenerationError);
                return listEvents;
            }

            newProcessDefinition = processAPI.deploy(newDesign);
        } catch (Exception e) {
            listEvents.add(new BEvent(eventProcessGeneration, e, e.getMessage()));
        }
        return listEvents;
    }

    public List<BEvent> copyActorsMapping() {
        List<BEvent> listEvents = new ArrayList<>();
        try {
            // contract violation : method done must be called before
            if (newProcessDefinition == null) {
                listEvents.add(eventGenerationError);
                return listEvents;
            }

            List<ActorInstance> listActors = processAPI.getActors(processDesign.processDeployment.getProcessId(), 0, 10000, ActorCriterion.NAME_ASC);
            Map<String, ActorInstance> mapActors = new HashMap<>();
            for (ActorInstance actor : listActors)
                mapActors.put(actor.getName(), actor);

            List<ActorInstance> listNewActors = processAPI.getActors(newProcessDefinition.getId(), 0, 10000, ActorCriterion.NAME_ASC);
            for (ActorInstance newActor : listNewActors) {
                ActorInstance actor = mapActors.get(newActor.getName());
                if (actor == null) {
                    // there is a probleme in the mapping
                    listEvents.add(new BEvent(eventCantFindActor, "Actor [" + newActor.getName() + "]"));
                    continue;
                }
                List<ActorMember> listMembers = processAPI.getActorMembers(actor.getId(), 0, 10000);
                for (ActorMember member : listMembers) {
                    if (member.getGroupId() > 0 && member.getRoleId() > 0)
                        processAPI.addRoleAndGroupToActor(newActor.getId(), member.getRoleId(), member.getGroupId());
                    else if (member.getRoleId() > 0)
                        processAPI.addRoleToActor(newActor.getId(), member.getRoleId());
                    else if (member.getGroupId() > 0)
                        processAPI.addGroupToActor(newActor.getId(), member.getGroupId());
                    else if (member.getUserId() > 0)
                        processAPI.addUserToActor(newActor.getId(), member.getUserId());
                }

            }

        } catch (Exception e) {
            listEvents.add(new BEvent(eventProcessGeneration, e, e.getMessage()));
        }
        return listEvents;
    }

    public List<BEvent> enable() {
        List<BEvent> listEvents = new ArrayList<>();
        try {
            if (newProcessDefinition == null) {
                listEvents.add(eventGenerationError);
                return listEvents;
            }
            processAPI.enableProcess(newProcessDefinition.getId());
        } catch (Exception e) {
            listEvents.add(new BEvent(eventProcessEnable, e, e.getMessage()));
        }
        return listEvents;
    }

    /**
     * @param version
     * @return
     */
    private String calculateNextVersion(String version) {
        try {
            long versionNumber = Long.valueOf(version);
            return String.valueOf(versionNumber + 1);
        } catch (Exception e) {
            return version + "(1)";
        }
    }

    /**
     * tool : calulated the next version of the process
     * 
     * @param currentVersion
     * @param mainRelease
     * @return
     */
    public String getNextProcessVersion(String currentVersion, boolean mainRelease) {
        StringTokenizer st = new StringTokenizer(currentVersion, ".");
        List<String> decomposition = new ArrayList<>();
        while (st.hasMoreElements())
            decomposition.add((String) st.nextElement());
        if (mainRelease)
            decomposition.set(0, calculateNextVersion(decomposition.get(0)));
        else
            decomposition.set(decomposition.size() - 1, calculateNextVersion(decomposition.get(decomposition.size() - 1)));
        // recompose
        StringBuilder version = new StringBuilder();
        for (int i = 0; i < decomposition.size(); i++) {
            if (i > 0)
                version.append(".");
            version.append(decomposition.get(i));
        }
        return version.toString();
    }

    public void test() {
        final ProcessDefinitionBuilder processDefinitionBuilder = new ProcessDefinitionBuilder()
                .createNewInstance("invalid", "1.0");
        processDefinitionBuilder.addStartEvent("start");
        processDefinitionBuilder.addGateway("GA", GatewayType.PARALLEL);
        processDefinitionBuilder.addAutomaticTask("auto2");
        processDefinitionBuilder.addEndEvent("end");
        processDefinitionBuilder.addTransition("start", "GA");
        processDefinitionBuilder.addTransition("GA", "auto2");
        processDefinitionBuilder.addTransition("auto2", "end");
        try {
            DesignProcessDefinition designProcessDefinition = processDefinitionBuilder.done();
            ProcessDefinition processDefinition = processAPI.deploy(designProcessDefinition);
            processAPI.enableProcess(processDefinition.getId());

        } catch (InvalidProcessDefinitionException | AlreadyExistsException | ProcessDeployException | ProcessDefinitionNotFoundException | ProcessEnablementException e) {
            logger.severe("Error during tet process " + e.getMessage());
        }
    }
}
