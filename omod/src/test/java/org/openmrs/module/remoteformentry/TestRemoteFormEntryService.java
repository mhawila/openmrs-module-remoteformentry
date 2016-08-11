package org.openmrs.module.remoteformentry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.openmrs.EncounterType;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PersonAddress;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonName;
import org.openmrs.Relationship;
import org.openmrs.api.EncounterService;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

/**
 * This class tests the different methods in the RemoteFormEntryService
 */
public class TestRemoteFormEntryService extends BaseModuleContextSensitiveTest {

	private Log log = LogFactory.getLog(getClass());

	/**
	 * Tests create/delete/get of RemoteFormEntryPendingQueues
	 * 
	 * @throws Exception
	 */
	@Test
	public void testRemoteFormEntryPendingQueue() throws Exception {

		log.debug("Starting remoteformentry pending queue tests");

		RemoteFormEntryService remoteService = (RemoteFormEntryService) Context.getService(RemoteFormEntryService.class);

		// get the original number of queue items so we have a base
		Integer origQueueSize = remoteService.getRemoteFormEntryPendingQueueSize();
		assertTrue(origQueueSize >= 0);

		// create the pending queue object we'll create
		RemoteFormEntryPendingQueue pendingQueue = new RemoteFormEntryPendingQueue();
		pendingQueue.setCreator(Context.getAuthenticatedUser());
		pendingQueue.setDateCreated(new Date());
		pendingQueue.setFormData("Some form data");

		// create the pending queue in the database (filesystem)
		remoteService.createRemoteFormEntryPendingQueue(pendingQueue);

		// check to make sure we have exactly one more pending item now
		Integer newQueueSize = remoteService.getRemoteFormEntryPendingQueueSize();
		assertEquals(newQueueSize.intValue(), origQueueSize + 1);

		// get all of the items
		List<RemoteFormEntryPendingQueue> pendingItems = remoteService.getRemoteFormEntryPendingQueues();
		assertEquals(newQueueSize.intValue(), pendingItems.size());

		// get the next queue item we should parse.
		RemoteFormEntryPendingQueue nextQueueItem = remoteService.getNextRemoteFormEntryPendingQueue();
		assertEquals(pendingItems.get(0), nextQueueItem);

		// test deleting the last queue item (theoretically, the one we just
		// created)
		RemoteFormEntryPendingQueue lastQueueItem = pendingItems.get(pendingItems.size() - 1);
		remoteService.deleteRemoteFormEntryPendingQueue(lastQueueItem);

		// make sure the list size went down by one
		newQueueSize = remoteService.getRemoteFormEntryPendingQueueSize();
		assertEquals(newQueueSize.intValue(), origQueueSize.intValue());

		// make sure the file was deleted
		assertFalse(new File(lastQueueItem.getFileSystemUrl()).exists());
	}

	/**
	 * This method tests the set/get initial encounter types methods in the
	 * remoteformentryservice
	 * 
	 * @throws Exception
	 */
	@Test
	public void testInitialEncounterTypes() throws Exception {
		
		RemoteFormEntryService remoteService = (RemoteFormEntryService) Context.getService(RemoteFormEntryService.class);
		EncounterService encService = Context.getEncounterService();
		
		List<EncounterType> encounterTypes = remoteService.getInitialEncounterTypes();
		
		// this list shouldn't ever be null. empty maybe, but not null
		assertNotNull(encounterTypes);
		
		List<Integer> encounterTypeIds = new Vector<Integer>();
		for (EncounterType encType : encounterTypes) {
			encounterTypeIds.add(encType.getEncounterTypeId());
		}
		
		try {
			Integer originalSize = encounterTypeIds.size();
			
			// get an encounter type and add it to the list
			EncounterType encType = encService.getAllEncounterTypes().get(0);
			encounterTypeIds.add(encType.getEncounterTypeId());
			
			remoteService.setInitialEncounterTypes(encounterTypeIds);
			
			List<EncounterType> newEncounterTypes = remoteService.getInitialEncounterTypes();
			
			assertEquals(originalSize + 1, newEncounterTypes.size());
			
			
		} finally {
			// restore the initial encounter types
			List<Integer> origEncounterTypeIds = new Vector<Integer>();
			for (EncounterType encType : encounterTypes) {
				encounterTypeIds.add(encType.getEncounterTypeId());
			}
			remoteService.setInitialEncounterTypes(origEncounterTypeIds);
		}
		
	}
	
	/**
	 * This method makes sure that all fields in a remote form are actually added to 
	 * a newly created patient
	 * 
	 * @throws Exception
	 */
	@Test
	public void testPatientCreation() throws Exception {
		executeDataSet("org/openmrs/module/remoteformentry/remoteformentrydataset.xml");
		
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		XPathFactory xpf = XPathFactory.newInstance();
		XPath xp = xpf.newXPath();
		
		File xmlFile = new File("src/test/resources/org/openmrs/module/remoteformentry/remotelyEnteredForm.xml");
		log.debug("xmlFile location: " + xmlFile.getAbsolutePath());
		
		Document doc = db.parse(xmlFile);
		
		RemoteFormEntryService remoteService = (RemoteFormEntryService) Context.getService(RemoteFormEntryService.class);
		PatientService patientService = Context.getPatientService();
		
		Patient createdPatient = remoteService.createPatientInDatabase(doc, xp);
		
		try {
			// check that the returned patient has all of the right 
			// attributes according to the xml file submitted
			//assertPatientHasAllProperties(createdPatient);
			
			// fetch the patient by identifier to make sure that the db has them
			List<Patient> lookedUpPatients = patientService.getPatients(null, "123456789", null);
			
			assertTrue(lookedUpPatients.size() == 1);
			
			// make sure this looked up patient has all the attributes too
			Patient lookedUpPatient = (Patient)lookedUpPatients.toArray()[0];
			assertPatientHasAllProperties(lookedUpPatient);
		}
		finally {
			patientService.purgePatient(createdPatient);
		}
	}

	/**
     * Compares the given patient to all of the expected values in the xml file
     * 
     * Asserts each element's veracity
     * 
     * @see ./remotelyEnteredForm.xml
     * 
     * @param createdPatient
     */
    private void assertPatientHasAllProperties(Patient patient) throws Exception {
    	// compare UUID
    	assertEquals("UUID is not valid", RemoteFormEntryUtilTest.SAMPLE_XML_PERSON_UUID, patient.getUuid());
    	
    	// compare the names
    	Set<PersonName> names = patient.getNames();
    	assertTrue(names.size() + " is not valid", names.size() == 2);
    	// the first one returned should be the preferred one
    	PersonName firstName = (PersonName)names.toArray()[0];
    	PersonName name = patient.getPersonName();
    	assertEquals(name, firstName);
    	assertTrue(name.equalsContent(firstName));
    	
    	if (name.isPreferred() == false) {
    		// put break point here to debug a bit
    		assertTrue(false);
    	}
    	assertTrue(name.isPreferred());
    	assertTrue(name.getGivenName() + " is not valid", "GivenName".equals(name.getGivenName()));
    	assertTrue(name.getMiddleName() + " is not valid", "MiddleName".equals(name.getMiddleName()));
    	assertTrue(name.getFamilyName() + " is not valid", "FamilyName".equals(name.getFamilyName()));
    	assertTrue(name.getCreator() + " is not valid", name.getCreator().getId().equals(1));
    	assertFalse(name.isVoided());
    	
    	PersonName secondName = (PersonName)names.toArray()[1];
    	assertNotSame(name, secondName);
    	assertFalse(name.equalsContent(secondName));
    	
    	assertTrue(secondName.getGivenName() + " is not valid", "GivenName2".equals(secondName.getGivenName()));
    	assertTrue(secondName.getMiddleName() + " is not valid", "MiddleName2".equals(secondName.getMiddleName()));
    	assertTrue(secondName.getFamilyName() + " is not valid", "FamilyName2".equals(secondName.getFamilyName()));
    	assertTrue(secondName.getCreator() + " is not valid", secondName.getCreator().getId().equals(1));
    	assertFalse(secondName.isPreferred());
    	assertTrue(secondName.isVoided());
    	
    	// compare the identifiers
    	Set<PatientIdentifier> identifiers = patient.getIdentifiers();
    	assertTrue(identifiers.size() + " is not valid", identifiers.size() == 2);
    	
    	PatientIdentifier prefIdentifier = patient.getPatientIdentifier();
		PatientIdentifier firstIdentifier = null, secondIdentifier = null;
		if(prefIdentifier.equalsContent((PatientIdentifier)identifiers.toArray()[0])) {
			firstIdentifier = (PatientIdentifier)identifiers.toArray()[0];
			secondIdentifier = (PatientIdentifier)identifiers.toArray()[1];
		} else {
			firstIdentifier = (PatientIdentifier)identifiers.toArray()[1];
			secondIdentifier = (PatientIdentifier)identifiers.toArray()[0];
		}

    	assertEquals(prefIdentifier, firstIdentifier);
    	assertTrue(prefIdentifier.equalsContent(firstIdentifier));
    	
    	assertTrue(prefIdentifier.getIdentifier() + " is not valid", "123456789".equals(prefIdentifier.getIdentifier()));
    	assertTrue(prefIdentifier.getIdentifierType() + " is not valid", prefIdentifier.getIdentifierType().getId() == 2);
    	assertTrue(prefIdentifier.getLocation() + " is not valid", prefIdentifier.getLocation().getId() == 1);
    	assertTrue(prefIdentifier.getCreator() + " is not valid", prefIdentifier.getCreator().getId().equals(1));
    	assertTrue(prefIdentifier.isPreferred());
    	assertFalse(prefIdentifier.isVoided());
    	
    	assertTrue(secondIdentifier.getIdentifier() + " is not valid", "1234567890".equals(secondIdentifier.getIdentifier()));
    	assertTrue(secondIdentifier.getIdentifierType() + " is not valid", secondIdentifier.getIdentifierType().getId() == 4);
    	assertTrue(secondIdentifier.getLocation() + " is not valid", secondIdentifier.getLocation().getId() == 1);
    	assertTrue(secondIdentifier.getCreator() + " is not valid", secondIdentifier.getCreator().getId().equals(1));
    	assertFalse(secondIdentifier.isPreferred());
    	assertTrue(secondIdentifier.isVoided());
    	
    	// compare the addresses
    	Set<PersonAddress> addresses = patient.getAddresses();
    	assertTrue(addresses.size() + " is not valid", addresses.size() == 2);
    	
    	PersonAddress prefAddress = patient.getPersonAddress();
    	PersonAddress firstAddress = (PersonAddress)addresses.toArray()[0];
    	assertEquals(prefAddress, firstAddress);
    	assertTrue(prefAddress.equalsContent(firstAddress));
    	
    	if (prefAddress.isPreferred() == false) {
    		// put break point here to debug a bit
    		assertTrue(false);
    	}
    	assertTrue(prefAddress.getAddress1() + " is not valid", "address1".equals(prefAddress.getAddress1()));
    	assertTrue(prefAddress.getAddress2() + " is not valid", "address2".equals(prefAddress.getAddress2()));
    	assertTrue(prefAddress.getCityVillage() + " is not valid", "cityVillage".equals(prefAddress.getCityVillage()));
    	assertTrue(prefAddress.getCountry() + " is not valid", "country".equals(prefAddress.getCountry()));
    	assertTrue(prefAddress.getCountyDistrict() + " is not valid", "countyDistrict".equals(prefAddress.getCountyDistrict()));
    	assertTrue(prefAddress.getLatitude() + " is not valid", "latitude".equals(prefAddress.getLatitude()));
    	assertTrue(prefAddress.getLongitude() + " is not valid", "longitude".equals(prefAddress.getLongitude()));
    	assertTrue(prefAddress.getNeighborhoodCell() + " is not valid", "neighborhoodCell".equals(prefAddress.getNeighborhoodCell()));
    	assertTrue(prefAddress.getPostalCode() + " is not valid", "postalCode".equals(prefAddress.getPostalCode()));
    	assertTrue(prefAddress.getRegion() + " is not valid", "region".equals(prefAddress.getRegion()));
    	assertTrue(prefAddress.getStateProvince() + " is not valid", "stateProvince".equals(prefAddress.getStateProvince()));
    	assertTrue(prefAddress.getSubregion() + " is not valid", "subRegion".equals(prefAddress.getSubregion()));
    	assertTrue(prefAddress.getCreator() + " is not valid", "townshipDivision".equals(prefAddress.getTownshipDivision()));
    	assertTrue(prefAddress.getCreator().getId().equals(1));
    	assertTrue(prefAddress.isPreferred());
    	assertFalse(prefAddress.isVoided());
    	
    	PersonAddress secondAddress = (PersonAddress)addresses.toArray()[1];
    	assertTrue(secondAddress.getAddress1() + " is not valid", "address12".equals(secondAddress.getAddress1()));
    	assertTrue(secondAddress.getAddress2() + " is not valid", "address22".equals(secondAddress.getAddress2()));
    	assertTrue(secondAddress.getCityVillage() + " is not valid", "cityVillage2".equals(secondAddress.getCityVillage()));
    	assertTrue(secondAddress.getCountry() + " is not valid", "country2".equals(secondAddress.getCountry()));
    	assertTrue(secondAddress.getCountyDistrict() + " is not valid", "countyDistrict2".equals(secondAddress.getCountyDistrict()));
    	assertTrue(secondAddress.getLatitude() + " is not valid", "latitude2".equals(secondAddress.getLatitude()));
    	assertTrue(secondAddress.getLongitude() + " is not valid", "longitude2".equals(secondAddress.getLongitude()));
    	assertTrue(secondAddress.getNeighborhoodCell() + " is not valid", "neighborhoodCell2".equals(secondAddress.getNeighborhoodCell()));
    	assertTrue(secondAddress.getPostalCode() + " is not valid", "postalCode2".equals(secondAddress.getPostalCode()));
    	assertTrue(secondAddress.getRegion() + " is not valid", "region2".equals(secondAddress.getRegion()));
    	assertTrue(secondAddress.getStateProvince() + " is not valid", "stateProvince2".equals(secondAddress.getStateProvince()));
    	assertTrue(secondAddress.getSubregion() + " is not valid", "subRegion2".equals(secondAddress.getSubregion()));
    	assertTrue(secondAddress.getTownshipDivision() + " is not valid", "townshipDivision2".equals(secondAddress.getTownshipDivision()));
    	assertTrue(secondAddress.getCreator() + " is not valid", secondAddress.getCreator().getId().equals(1));
    	assertFalse(secondAddress.isPreferred());
    	assertTrue(secondAddress.isVoided());
    	
    	// compare the PersonAttributes
    	Set<PersonAttribute> attributes = patient.getAttributes();
    	assertTrue(attributes.size() + " is not valid", attributes.size() == 2);
    	
    	for (PersonAttribute attribute : attributes) {
    		if (attribute.isVoided())
    			assertTrue(attribute.getValue() + " is not valid", "123".equals(attribute.getValue()));
    		else
    			assertTrue(attribute.getValue() + " is not valid", "5".equals(attribute.getValue()));
    		
	    	assertTrue(attribute.getAttributeType() + " is not valid", attribute.getAttributeType().getId() == 8);
	    	assertTrue(attribute.getCreator() + " is not valid", attribute.getCreator().getId().equals(1));
    	}
    	
    	// check attrs on the person object
    	DateFormat format = new SimpleDateFormat("MM/dd/yyyy");
    	assertTrue(patient.getBirthdate() + " is not valid", patient.getBirthdate().equals(format.parse("09/19/1965")));
    	assertTrue(patient.isBirthdateEstimated());
    	assertTrue(patient.isDead());
    	assertTrue(patient.getDeathDate() + " is not valid", patient.getDeathDate().equals(format.parse("01/01/2001")));
    	assertTrue(patient.getCauseOfDeath() + " is not valid", patient.getCauseOfDeath().getConceptId() == 22);
    	assertTrue(patient.getGender() + " is not valid", patient.getGender().equals("M"));    	
    	
    	// compare the relationships
    	List<Relationship> relationships = Context.getPersonService().getRelationshipsByPerson(patient);
    	assertTrue(relationships.size() + " is not the right # of relationships", relationships.size() == 1);
    	
    	for (Relationship rel : relationships) {
    		if (rel.getRelationshipType().getId() == 1) {
    			assertEquals("d44fdcf2-45ff-11de-abd9-0010c6dffd0f", rel.getPersonA().getUuid()); // the newly created person for this relationship
    			assertEquals(patient, rel.getPersonB()); // the patient for this form created during this remoteformentry
    		}
    		else {
    			assertEquals("86526ed6-3c11-11de-a0ba-001e378eb67e", rel.getPersonB().getUuid()); // the patient that already existed
    			assertEquals(patient, rel.getPersonA()); // the patient for this form created during this remoteformentry
    		}
    	}
    	
    }

}
