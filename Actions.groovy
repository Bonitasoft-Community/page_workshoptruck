import java.lang.management.RuntimeMXBean;
import java.lang.management.ManagementFactory;

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.logging.Logger;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.lang.Runtime;

import org.json.simple.JSONObject;
import org.codehaus.groovy.tools.shell.CommandAlias;
import org.json.simple.JSONArray;
import org.json.simple.JSONValue;



import javax.naming.Context;
import javax.naming.InitialContext;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.sql.Clob;
import java.util.Date;

import org.apache.commons.lang3.StringEscapeUtils


import org.bonitasoft.engine.identity.User;
import org.bonitasoft.engine.search.SearchOptionsBuilder;
import org.bonitasoft.engine.search.SearchResult;
import org.bonitasoft.engine.service.TenantServiceSingleton

import org.bonitasoft.web.extension.page.PageContext;
import org.bonitasoft.web.extension.page.PageController;
import org.bonitasoft.web.extension.page.PageResourceProvider;

import org.bonitasoft.engine.exception.AlreadyExistsException;
import org.bonitasoft.engine.exception.BonitaHomeNotSetException;
import org.bonitasoft.engine.exception.CreationException;
import org.bonitasoft.engine.exception.DeletionException;
import org.bonitasoft.engine.exception.ServerAPIException;
import org.bonitasoft.engine.exception.UnknownAPITypeException;
import org.bonitasoft.engine.bpm.process.ProcessDefinitionNotFoundException;

import org.bonitasoft.engine.api.TenantAPIAccessor;
import org.bonitasoft.engine.bpm.flownode.HumanTaskInstance;
import org.bonitasoft.engine.bpm.flownode.HumanTaskInstanceSearchDescriptor;
import org.bonitasoft.engine.bpm.process.ActivationState
import org.bonitasoft.engine.bpm.process.ArchivedProcessInstance;
import org.bonitasoft.engine.bpm.process.ArchivedProcessInstancesSearchDescriptor;
import org.bonitasoft.engine.bpm.process.ProcessInstance;
import org.bonitasoft.engine.bpm.process.ProcessInstanceSearchDescriptor;
import org.bonitasoft.engine.business.data.BusinessDataRepository
import org.bonitasoft.engine.session.APISession;
import org.bonitasoft.engine.api.ProcessAPI;
import org.bonitasoft.engine.api.PageAPI;
import org.bonitasoft.engine.api.IdentityAPI;
import org.bonitasoft.engine.command.CommandDescriptor;
import org.bonitasoft.engine.command.CommandCriterion;
import org.bonitasoft.engine.bpm.flownode.ActivityInstance;
import org.bonitasoft.engine.bpm.process.ProcessDeploymentInfo;
import org.bonitasoft.engine.bpm.process.ProcessDeploymentInfoSearchDescriptor
import org.bonitasoft.engine.identity.UserSearchDescriptor;
import org.bonitasoft.engine.search.Order;
import org.bonitasoft.engine.bpm.process.ProcessDeploymentInfo;



import org.bonitasoft.log.event.BEvent;
import org.bonitasoft.log.event.BEvent.Level;
import org.bonitasoft.log.event.BEventFactory;

import org.bonitasoft.properties.BonitaProperties;
import org.bonitasoft.engine.service.TenantServiceAccessor;
import org.bonitasoft.engine.service.TenantServiceSingleton;

import org.bonitasoft.workshoptruck.WorkshopTruckAPI;
import org.bonitasoft.workshoptruck.WorkshopTruckAPI.InsertActivityInformation;


public class Actions {

    private static Logger logger= Logger.getLogger("org.bonitasoft.custompage.longboard.groovy");
    
    
    private static EVENT_USERS_FOUND = new BEvent("org.bonitasoft.custompage.ping", 1, Level.INFO, "Number of users found in the system", "", "", "");
    private static EVENT_FAKE_ERROR  = new BEvent("com.bonitasoft.ping", 1, Level.APPLICATIONERROR, "Fake error", "This is not a real error", "No consequence", "don't call anybody");

    
      // 2018-03-08T00:19:15.04Z
    public final static SimpleDateFormat sdfJson = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    public final static SimpleDateFormat sdfHuman = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /* -------------------------------------------------------------------- */
    /*                                                                      */
    /* doAction */
    /*                                                                      */
    /* -------------------------------------------------------------------- */

    public static Index.ActionAnswer doAction(HttpServletRequest request, String paramJsonSt, HttpServletResponse response, PageResourceProvider pageResourceProvider, PageContext pageContext) {
                
        // logger.info("#### PingActions:Actions start");
        Index.ActionAnswer actionAnswer = new Index.ActionAnswer();         
        Object jsonParam = (paramJsonSt==null ? null : JSONValue.parse(paramJsonSt));
          
        try {
            String action=request.getParameter("action");
            logger.info("#### log:Actions  action is["+action+"] !");
            if (action==null || action.length()==0 )
            {
                actionAnswer.isManaged=false;
                logger.info("#### log:Actions END No Actions");
                return actionAnswer;
            }
            actionAnswer.isManaged=true;
            
            // Hello
            APISession apiSession = pageContext.getApiSession();
            HttpSession httpSession = request.getSession();            
            ProcessAPI processAPI = TenantAPIAccessor.getProcessAPI(apiSession);
            PageAPI pageAPI = TenantAPIAccessor.getCustomPageAPI(apiSession);
            
            IdentityAPI identityAPI = TenantAPIAccessor.getIdentityAPI(apiSession);
			
            long tenantId = apiSession.getTenantId();          
            TenantServiceAccessor tenantServiceAccessor = TenantServiceSingleton.getInstance(tenantId);             

                
            WorkshopTruckAPI workshopTruckAPI = new WorkshopTruckAPI();
            if ("loadDesign".equals(action)) {
               actionAnswer.responseMap= workshopTruckAPI.loadProcess(jsonParam.get("processname"), processAPI, pageAPI);
            }            
            else if ("insertActivity".equals(action)) {
                InsertActivityInformation insertActivityInformation = InsertActivityInformation.getInstance( jsonParam )

                actionAnswer.responseMap= workshopTruckAPI.insertActivity( insertActivityInformation, processAPI, pageAPI);
             }             
			else if ("queryprocess".equals(action))
            {
                
                List listProcesses = new ArrayList();
                String processNameFilter = (jsonParam == null ? "" : jsonParam.get("queryfilter"));
                String processState = (jsonParam == null ? "" : jsonParam.get("stateprocess"));
                SearchOptionsBuilder searchOptionsBuilder = new SearchOptionsBuilder(0,20);
                // searchOptionsBuilder.greaterOrEquals(ProcessDeploymentInfoSearchDescriptor.NAME, processFilter);
                // searchOptionsBuilder.lessOrEquals(ProcessDeploymentInfoSearchDescriptor.NAME, processFilter+"z");
                searchOptionsBuilder.searchTerm(processNameFilter);

                if ("ONLYDISABLED".equals(processState))
                {
                    searchOptionsBuilder.filter(ProcessDeploymentInfoSearchDescriptor.ACTIVATION_STATE, ActivationState.DISABLED.toString());
                }
                if ("ONLYENABLED".equals(processState))
                {
                        searchOptionsBuilder.filter(ProcessDeploymentInfoSearchDescriptor.ACTIVATION_STATE, ActivationState.ENABLED.toString());
                 }
                 logger.info("#### queryprocess["+processNameFilter+"] State["+processState+"]");
                 
                searchOptionsBuilder.sort( ProcessDeploymentInfoSearchDescriptor.NAME,  Order.ASC);
                searchOptionsBuilder.sort( ProcessDeploymentInfoSearchDescriptor.VERSION,  Order.ASC);
                Set<String> setProcessesWithoutVersion=new HashSet<String>();

                SearchResult<ProcessDeploymentInfo> searchResult = processAPI.searchProcessDeploymentInfos(searchOptionsBuilder.done() );
                // logger.info("TruckMilk:Search process deployment containing ["+processFilter+"] - found "+searchResult.getCount());

                for (final ProcessDeploymentInfo processDeploymentInfo : searchResult.getResult())
                {
                    final Map<String, Object> oneRecord = new HashMap<String, Object>();
                    oneRecord.put("display", processDeploymentInfo.getName() + " (" + processDeploymentInfo.getVersion()+")");
                    oneRecord.put("id", processDeploymentInfo.getName() + " (" + processDeploymentInfo.getVersion()+")");
                    listProcesses.add( oneRecord );
                    // setProcessesWithoutVersion.add(processDeploymentInfo.getName());
                }
                // add all processes without version
                for (String processName : setProcessesWithoutVersion ) {
                    final Map<String, Object> oneRecord = new HashMap<String, Object>();
                    oneRecord.put("display", processName);
                    oneRecord.put("id", processName);
                    listProcesses.add( oneRecord );
                }
                // sort the result again to have the "process without version" at the correct place
                
                Collections.sort(listProcesses, new Comparator< Map<String, Object>>()
                   {
                     public int compare( Map<String, Object> s1,
                                         Map<String, Object> s2)
                     {
                       return ((String)s1.get("display")).compareTo( ((String)s2.get("display")));
                     }
                   });
                actionAnswer.responseMap.put("listProcess", listProcesses);
                actionAnswer.responseMap.put("nbProcess", searchResult.getCount());
            }
            else if ("queryusers".equals(action))
            {
               
				List listUsers = new ArrayList();
				final SearchOptionsBuilder searchOptionBuilder = new SearchOptionsBuilder(0, 100000);
           		// http://documentation.bonitasoft.com/?page=using-list-and-search-methods
            	searchOptionBuilder.filter(UserSearchDescriptor.ENABLED, Boolean.TRUE);
            	searchOptionBuilder.searchTerm( jsonParam==null ? "" : jsonParam.get("userfilter") );

            	searchOptionBuilder.sort(UserSearchDescriptor.LAST_NAME, Order.ASC);
            	searchOptionBuilder.sort(UserSearchDescriptor.FIRST_NAME, Order.ASC);
            	final SearchResult<User> searchResult = identityAPI.searchUsers(searchOptionBuilder.done());
            	for (final User user : searchResult.getResult())
            	{
                	final Map<String, Object> oneRecord = new HashMap<String, Object>();
                // oneRecord.put("display", user.getFirstName()+" " + user.getLastName()  + " (" + user.getUserName() + ")");
	                oneRecord.put("display", user.getLastName() + "," + user.getFirstName() + " (" + user.getUserName() + ")");
    	            oneRecord.put("id", user.getId());
    	            listUsers.add( oneRecord );
    	        }
                 actionAnswer.responseMap.put("listUsers", listUsers);

            }	
			
             
                 
            
            logger.info("#### log:Actions END responseMap ="+actionAnswer.responseMap.size());
            return actionAnswer;
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String exceptionDetails = sw.toString();
            logger.severe("#### log:Groovy Exception ["+e.toString()+"] at "+exceptionDetails);
            actionAnswer.isResponseMap=true;
            actionAnswer.responseMap.put("Error", "log:Groovy Exception ["+e.toString()+"] at "+exceptionDetails);
            

            
            return actionAnswer;
        }
    }

    
}
