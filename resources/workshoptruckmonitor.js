'use strict';
/**
 *
 */

(function() {


var appCommand = angular.module('WorkshopTruckApp', ['googlechart', 'ui.bootstrap','ngSanitize', 'ngModal', 'ngMaterial']);


/* Material : for the autocomplete
 * need 
  <script src="https://ajax.googleapis.com/ajax/libs/angularjs/1.5.5/angular.min.js"></script>
  <script src="https://ajax.googleapis.com/ajax/libs/angularjs/1.5.5/angular-animate.min.js"></script>
  <script src="https://ajax.googleapis.com/ajax/libs/angularjs/1.5.5/angular-aria.min.js"></script>
  <script src="https://ajax.googleapis.com/ajax/libs/angularjs/1.5.5/angular-messages.min.js"></script>

  <!-- Angular Material Library -->
  <script src="https://ajax.googleapis.com/ajax/libs/angular_material/1.1.0/angular-material.min.js">
 */



// --------------------------------------------------------------------------
//
// Controler Ping
//
// --------------------------------------------------------------------------

// Ping the server
appCommand.controller('WorkshopControler',
	function ( $http, $scope,$sce,$filter ) {

	this.pingdate='';
	this.pinginfo='';
	this.listevents='';
	this.inprogress=false;
	
	this.selected= {};
	this.loadDesign = function()
	{
		console.log("Load Design");
		
		var self=this;
		self.inprogress=true;
		var d = new Date();
		var param={ 'processname' :  this.process.name.id};
		
		var json = encodeURI( angular.toJson( param, false));

		$http.get( '?page=custompage_workshoptruck&action=loadDesign&paramjson='+json+'&t='+d.getTime() )
				.success( function ( jsonResult, statusHttp, headers, config ) {
					// connection is lost ?
					if (statusHttp==401 || typeof jsonResult === 'string') {
						console.log("Redirected to the login page !");
						window.location.reload();
					}
					console.log("design",jsonResult);
					self.design							= jsonResult;
					self.process.processDefinitionId 	= jsonResult.processDefinitionId;
					self.designlistevents				= jsonResult.listevents;
					self.inprogress=false;
					
					
						
				})
				.error( function() {
					self.inprogress=false;
					});
		
		
				
	}

	this.getUrlProcessVisu = function() {
		return "/bonita/portal.js/#/admin/monitoring/"+this.process.processDefinitionId+"?id="+this.process.processDefinitionId;
	}
	
	
	this.controlInsertActivity = function() {
		var message="";
		if (! this.process.processDefinitionId)
			message += "Select a process, ";
		if (! this.process.transitionId)
			message += "Select transition where the new activity will be added, ";
		if (! this.process.nameNewActivity)
			message += "Give a nameto the new activity, ";
		if (! this.process.modelId)
			message += "Select a model to create the new activity";
		return message;
	}
	this.insertActivity = function() {
		console.log("InsertActivity");
		
		var self=this;
		self.inprogress=true;
		var d = new Date();
		var param= this.process;
		
		var json = encodeURI( angular.toJson( param, false));

		$http.get( '?page=custompage_workshoptruck&action=insertActivity&paramjson='+json+'&t='+d.getTime() )
				.success( function ( jsonResult, statusHttp, headers, config ) {
					// connection is lost ?
					if (statusHttp==401 || typeof jsonResult === 'string') {
						console.log("Redirected to the login page !");
						window.location.reload();
					}
					console.log("insert:",angular.toJson(jsonResult));
					self.insertlistevents		= jsonResult.listevents;
					
					self.inprogress=false;
						
						
				})
				.error( function() {
					self.inprogress=false;
					});
				
	}
	
	// -----------------------------------------------------------------------------------------
	//  										Autocomplete
	// -----------------------------------------------------------------------------------------
	this.process = { "list":[] };
	
	this.query = function(queryName, textSearch) {
		console.log("query:Start");
		var self=this;
		self.inprogress=true;
		console.log("query ["+queryName+"] on ["+textSearch+"] inprogress<=true");

		var param={ 'queryfilter' :  textSearch};
		
		var json = encodeURI( angular.toJson( param, false));
		// 7.6 : the server force a cache on all URL, so to bypass the
		// cache, then create a different URL
		var d = new Date();
		
		console.log("query Call HTTP param="+json);
		return $http.get( '?page=custompage_workshoptruck&action='+queryName+'&paramjson='+json+'&t='+d.getTime() )
		.then( function ( jsonResult, statusHttp, headers, config ) {
			
			// connection is lost ?
			if (statusHttp==401 || typeof jsonResult === 'string') {
				console.log("Redirected to the login page !");
				window.location.reload();

			}
			self.inprogress=false;
			console.log("Query.receiveData HTTP inProgress<=false result="+angular.toJson(jsonResult.data, false));
			self.process.list 			=  jsonResult.data.listProcess;
			self.process.nbProcess		=  jsonResult.data.nbProcess;
			return self.process.list;
		}, function ( jsonResult ) {
			console.log("QueryUser HTTP THEN");
			self.inprogress=false;

		});

	  };
	  
	this.autocomplete={};
	
	this.queryUser = function(searchText) {
		var self=this;
		console.log("QueryUser HTTP CALL["+searchText+"]");
		
		self.autocomplete.inprogress=true;
		self.autocomplete.search = searchText;
		self.inprogress=true;
		
		var param={ 'userfilter' :  self.autocomplete.search};
		
		var json = encodeURI( angular.toJson( param, false));
		// 7.6 : the server force a cache on all URL, so to bypass the cache, then create a different URL
		var d = new Date();
		
		return $http.get( '?page=custompage_ping&action=queryusers&paramjson='+json+'&t='+d.getTime() )
		.then( function ( jsonResult, statusHttp, headers, config ) {
			// connection is lost ?
			if (statusHttp==401 || typeof jsonResult === 'string') {
				console.log("Redirected to the login page !");
				window.location.reload();
			}
			console.log("QueryUser HTTP SUCCESS.1 - result= "+angular.toJson(jsonResult, false));
			self.autocomplete.inprogress=false;
		 	self.autocomplete.listUsers =  jsonResult.data.listUsers;
			console.log("QueryUser HTTP SUCCESS length="+self.autocomplete.listUsers.length);
			self.inprogress=false;
	
			return self.autocomplete.listUsers;
		},  function ( jsonResult ) {
		console.log("QueryUser HTTP THEN");
		});

	  };
	  
	// -----------------------------------------------------------------------------------------
	//  										Excel
	// -----------------------------------------------------------------------------------------

	this.exportData = function () 
	{  
		//Start*To Export SearchTable data in excel  
	// create XLS template with your field.  
		var mystyle = {         
        headers:true,        
			columns: [  
			{ columnid: 'name', title: 'Name'},
			{ columnid: 'version', title: 'Version'},
			{ columnid: 'state', title: 'State'},
			{ columnid: 'deployeddate', title: 'Deployed date'},
			],         
		};  
	
        //get current system date.         
        var date = new Date();  
        $scope.CurrentDateTime = $filter('date')(new Date().getTime(), 'MM/dd/yyyy HH:mm:ss');          
		var trackingJson = this.listprocesses
        //Create XLS format using alasql.js file.  
        alasql('SELECT * INTO XLS("Process_' + $scope.CurrentDateTime + '.xls",?) FROM ?', [mystyle, trackingJson]);  
    };
    

	// -----------------------------------------------------------------------------------------
	//  										Properties
	// -----------------------------------------------------------------------------------------
	this.propsFirstName='';
	
	

	
	<!-- Manage the event -->
	this.getListEvents = function ( listevents ) {
		return $sce.trustAsHtml(  listevents );
	}
	<!-- Manage the Modal -->
	this.isshowDialog=false;
	this.openDialog = function()
	{
		this.isshowDialog=true;
	};
	this.closeDialog = function()
	{
		this.isshowDialog=false;
	}

});



})();