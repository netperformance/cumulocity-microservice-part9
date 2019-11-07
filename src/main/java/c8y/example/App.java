package c8y.example;


import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cumulocity.microservice.autoconfigure.MicroserviceApplication;
import com.cumulocity.microservice.settings.service.MicroserviceSettingsService;
import com.cumulocity.microservice.subscription.service.MicroserviceSubscriptionsService;
import com.cumulocity.model.Agent;
import com.cumulocity.model.ID;
import com.cumulocity.model.idtype.GId;
import com.cumulocity.rest.representation.identity.ExternalIDRepresentation;
import com.cumulocity.rest.representation.inventory.ManagedObjectRepresentation;
import com.cumulocity.rest.representation.operation.OperationRepresentation;
import com.cumulocity.sdk.client.SDKException;
import com.cumulocity.sdk.client.alarm.AlarmApi;
import com.cumulocity.sdk.client.devicecontrol.DeviceControlApi;
import com.cumulocity.sdk.client.devicecontrol.OperationCollection;
import com.cumulocity.sdk.client.devicecontrol.PagedOperationCollectionRepresentation;
import com.cumulocity.sdk.client.event.EventApi;
import com.cumulocity.sdk.client.identity.IdentityApi;
import com.cumulocity.sdk.client.inventory.InventoryApi;
import com.cumulocity.sdk.client.measurement.MeasurementApi;
import com.google.common.collect.Lists;

import c8y.IsDevice;
import c8y.Restart;


@MicroserviceApplication
@RestController
public class App{
		
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @RequestMapping("hello")
    public String greeting(@RequestParam(value = "name", defaultValue = "world") String name) {
        return "hello " + name + "!";
    }

    // You need the inventory API to handle managed objects e.g. creation. You will find this class within the C8Y java client library.
    private final InventoryApi inventoryApi;
    // you need the identity API to handle the external ID e.g. IMEI of a managed object. You will find this class within the C8Y java client library.
    private final IdentityApi identityApi;
    
    // you need the measurement API to handle measurements. You will find this class within the C8Y java client library.
    private final MeasurementApi measurementApi;
    
    // you need the alarm API to handle measurements.
    private final AlarmApi alarmApi;
    
    // you need the event API to handle measurements.
    private final EventApi eventApi;
    
    // you need the DeviceControlApi to get access to the operations
    private final DeviceControlApi deviceControlApi;
    
    // Microservice subscription
    private final MicroserviceSubscriptionsService subscriptionService;
        
    // To access the tenant options
    private final MicroserviceSettingsService microserviceSettingsService;
    
    @Autowired
    public App( InventoryApi inventoryApi, 
    			IdentityApi identityApi, 
    			MicroserviceSubscriptionsService subscriptionService,
    			MeasurementApi measurementApi,
    			MicroserviceSettingsService microserviceSettingsService,
    			AlarmApi alarmApi,
    			EventApi eventApi,
    			DeviceControlApi deviceControlApi) {
        this.inventoryApi = inventoryApi;
        this.identityApi = identityApi;
        this.subscriptionService = subscriptionService;
        this.measurementApi = measurementApi;
        this.microserviceSettingsService = microserviceSettingsService;
        this.alarmApi = alarmApi;
        this.eventApi = eventApi;
        this.deviceControlApi = deviceControlApi;
    }
    
    /*/
    // Create every x sec a new measurement
    @Scheduled(initialDelay=10000, fixedDelay=5000)
    public void startThread() {
    	subscriptionService.runForEachTenant(new Runnable() {
			@Override
			public void run() {
		    	try {
		    		// ....
		    		
				} catch (Exception e) {
					e.printStackTrace();
				} 
			}
		});
    }
    //*/
        
    // Create a new managed object + external ID (if not existing)  
    private ManagedObjectRepresentation resolveManagedObject() {
       	
    	try {
        	// check if managed object is existing. create a new one if the managed object is not existing
    		ExternalIDRepresentation externalIDRepresentation = identityApi.getExternalId(new ID("c8y_Serial", "Microservice-Part9_externalId"));
			return externalIDRepresentation.getManagedObject();    	    	

    	} catch(SDKException e) {
    		    		
    		// create a new managed object
			ManagedObjectRepresentation newManagedObject = new ManagedObjectRepresentation();
	    	newManagedObject.setName("Microservice-Part9");
	    	newManagedObject.setType("Microservice-Part9");
	    	newManagedObject.set(new IsDevice());	    	
	    	
	    	// add agent to managed object because only an agent is able to receive operations
	    	newManagedObject.set(new Agent());
	    	
	    	ManagedObjectRepresentation createdManagedObject = inventoryApi.create(newManagedObject);
	    	
	    	// create an external id and add the external id to an existing managed object
	    	ExternalIDRepresentation externalIDRepresentation = new ExternalIDRepresentation();
	    	// Definition of the external id
	    	externalIDRepresentation.setExternalId("Microservice-Part9_externalId");
	    	// Assign the external id to an existing managed object
	    	externalIDRepresentation.setManagedObject(createdManagedObject);
	    	// Definition of the serial
	    	externalIDRepresentation.setType("c8y_Serial");
	    	// Creation of the external id

	    	identityApi.create(externalIDRepresentation);
	    	
	    	return createdManagedObject;
    	}
    }
    
	@RequestMapping("createNewOperation")
	public String createNewOperation() {
		
		// Managed object representation will give you access to the ID of the managed object
		ManagedObjectRepresentation managedObjectRepresentation = resolveManagedObject();
	
		// Operational representation object (setup your operation)
		OperationRepresentation operationRepresentation = new OperationRepresentation();
		operationRepresentation.setDeviceId(managedObjectRepresentation.getId());
		operationRepresentation.set("Restart", "description");
		operationRepresentation.set(new Restart());
		
		// create a new operation
		deviceControlApi.create(operationRepresentation);
		
		return operationRepresentation.toJSON();
	}
    
    @RequestMapping("getAllOperations")
    private List<OperationRepresentation> getAllOperations() {
    	
    	// To get access to event operation representation
    	OperationCollection operationCollection = deviceControlApi.getOperations();
    	
    	// To get access to e.g. all operation page
    	PagedOperationCollectionRepresentation pagedOperationCollectionRepresentation = operationCollection.get();
    	
    	// Representation of a series of operation elements. Get all pages.
    	Iterable<OperationRepresentation> iterable = pagedOperationCollectionRepresentation.allPages();
    	
    	// Usage of google guava to create an operation list 
    	List<OperationRepresentation> operationRepresentationList = Lists.newArrayList(iterable);
    	
    	return operationRepresentationList;
    }
    
    @RequestMapping("getOperationById")
    public OperationRepresentation getOperationById(@RequestParam(value = "operationId") String operationId) {
		if(operationId.length()>=1) {
			try {
				// Use GId to transform the given id to a global c8y id
				OperationRepresentation operationRepresentation = deviceControlApi.getOperation(GId.asGId(operationId));
				
				return operationRepresentation;
			} catch(Exception e) {
				return null;
			}
		}
		return null;
    }
    
	@RequestMapping("changeOperationStatus")
	public void changeOperationStatus(@RequestParam(value = "operationId") String operationId, @RequestParam(value = "status") String operationStatus) {
		
		OperationRepresentation operationRepresentation = getOperationById(operationId);
		
		if(operationRepresentation!=null) {
			// possible operation status: PENDING -> EXECUTING -> SUCCESSFUL or FAILED  
			// PENDING is the default status after creation of an operation
			operationRepresentation.setStatus(operationStatus);
			
			deviceControlApi.update(operationRepresentation);
		}
	}
	
}