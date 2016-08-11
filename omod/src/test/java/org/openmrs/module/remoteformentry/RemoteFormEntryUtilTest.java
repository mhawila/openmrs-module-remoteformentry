package org.openmrs.module.remoteformentry;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.Person;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.PersonName;
import org.openmrs.Relationship;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.openmrs.test.Verifies;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class RemoteFormEntryUtilTest extends BaseModuleContextSensitiveTest {

	// used in various tests
	public static final String SAMPLE_XML_PERSON_UUID = "2178037d-f86b-4f12-8d8b-be3ebc220022";
	private PersonName testName = null;

	@Before
	public void setup() {
		testName = new PersonName();
		testName.setFamilyName("test");
		testName.setMiddleName("test");
		testName.setGivenName("test");
	}

	/**
	 * @see {@link RemoteFormEntryUtil#getRelationships(Patient,Document,XPath,User)}
	 */
	@Test
	@Verifies(value = "should not process relationship mappings with blank RelationshipTypeIds", method = "getRelationships(Patient,Document,XPath,User)")
	public void getRelationships_shouldNotProcessRelationshipMappingsWithBlankRelationshipTypeIds()
			throws Exception {
		// build all the variables that are passed to getRelationships()
		DocumentBuilder db = DocumentBuilderFactory.newInstance()
				.newDocumentBuilder();
		Document doc = db
				.parse(new File(
						"src/test/resources/org/openmrs/module/remoteformentry/remotelyEnteredForm.xml"));
		XPath xp = XPathFactory.newInstance().newXPath();
		Patient createdPatient = new Patient(1);
		User enterer = new User(1);

		// need the list of nodes to get the pre-evaluation size
		NodeList nodeList = (NodeList) xp.evaluate(
				RemoteFormEntryConstants.nodePrefix
						+ RemoteFormEntryConstants.PERSON_RELATIONSHIP, doc,
				XPathConstants.NODESET);

		// call the utility to get the relationships
		List<Relationship> relationships = RemoteFormEntryUtil
				.getRelationships(createdPatient, doc, xp, enterer);

		// there should be only one relationship defined that has a blank
		// relationship type id
		Assert.assertEquals(
				"relationship with a blank type id is being processed",
				nodeList.getLength() - 2, relationships.size());
	}

	/**
	 * @see {@link RemoteFormEntryUtil#getRelationships(Patient,Document,XPath,User)}
	 */
	@Test
	@Verifies(value = "should process relatives with blank identifiers", method = "getRelationships(Patient,Document,XPath,User)")
	public void getRelationships_shouldProcessRelativesWithBlankIdentifiers()
			throws Exception {
		// build all the variables that are passed to getRelationships()
		DocumentBuilder db = DocumentBuilderFactory.newInstance()
				.newDocumentBuilder();
		Document doc = db
				.parse(new File(
						"src/test/resources/org/openmrs/module/remoteformentry/remotelyEnteredFormWithBlankRelativeIdentifier.xml"));
		XPath xp = XPathFactory.newInstance().newXPath();
		Patient createdPatient = new Patient(1);
		User enterer = new User(1);

		// need the list of nodes to get the pre-evaluation size
		NodeList nodeList = (NodeList) xp.evaluate(
				RemoteFormEntryConstants.nodePrefix
						+ RemoteFormEntryConstants.PERSON_RELATIONSHIP, doc,
				XPathConstants.NODESET);

		// call the utility to get the relationships
		List<Relationship> relationships = RemoteFormEntryUtil
				.getRelationships(createdPatient, doc, xp, enterer);

		// there should be two relationships
		Assert.assertEquals("something went horribly wrong", 1, relationships.size());
		
		for (Relationship rel : relationships) {
    		if (rel.getRelationshipType().getId() == 1) {
				// the relationship of type "1" should be to a person who is not a patient
    			Assert.assertFalse(rel.getPersonA().isPatient());
    		}
    		else {
				// the other relationship should be to a person who is patient
    			Assert.assertTrue(rel.getPersonB().isPatient());
    		}
    	}
	}

	/**
	 * @see {@link RemoteFormEntryUtil#setPersonAttributes(Patient,Document,XPath,User)}
	 */
	@Test
	@Verifies(value = "should not duplicate existing attributes", method = "setPersonAttributes(Patient,Document,XPath,User)")
	public void setPersonAttributes_shouldNotDuplicateExistingAttributes()
			throws Exception {

		// create data to match what is in remotelyEnteredForm.xml

		Person person = new Person();
		person.setGender("M");
		
		// add two attributes of type=8 to the person

		PersonAttribute pa1 = new PersonAttribute();
		pa1.setAttributeType(new PersonAttributeType(8));
		pa1.setValue("5");
		pa1.setVoided(false);

		PersonAttribute pa2 = new PersonAttribute();
		pa2.setAttributeType(new PersonAttributeType(8));
		pa2.setValue("123");
		pa2.setVoided(true);
		pa2.setVoidReason("just because");
		pa2.setVoidedBy(new User(1));

		Set<PersonAttribute> pas = new TreeSet<PersonAttribute>();
		pas.add(pa1);
		pas.add(pa2);
		person.setAttributes(pas);
		
		// create the actual patient

		Patient patient = new Patient(person);

		PatientIdentifier pi1 = new PatientIdentifier();
		pi1.setIdentifierType(new PatientIdentifierType(4));
		pi1.setIdentifier("1234567890");
		pi1.setLocation(new Location(1));
		pi1.setPreferred(false);
		pi1.setVoided(true);
		pi1.setVoidReason("just because");
		pi1.setVoidedBy(new User(1));
		
		PatientIdentifier pi2 = new PatientIdentifier();
		pi2.setIdentifierType(new PatientIdentifierType(1));
		pi2.setIdentifier("123456789");
		pi2.setLocation(new Location(1));
		pi2.setPreferred(true);
		pi2.setVoided(false);

		Set<PatientIdentifier> pis = new TreeSet<PatientIdentifier>();
		pis.add(pi1);
		pis.add(pi2);
		patient.setIdentifiers(pis);

		patient.addName(testName);
		patient = Context.getPatientService().savePatient(patient);

		// build all the variables that are passed to setPersonAttributes()
		DocumentBuilder db = DocumentBuilderFactory.newInstance()
				.newDocumentBuilder();
		Document doc = db
				.parse(new File(
						"src/test/resources/org/openmrs/module/remoteformentry/remotelyEnteredForm.xml"));
		XPath xp = XPathFactory.newInstance().newXPath();
		User enterer = new User(1);

		Assert.assertEquals("something went horribly wrong", patient.getAttributes().size(), 2);
		
		// call the method
		RemoteFormEntryUtil.setPersonAttributes(patient, doc, xp, enterer);

		Assert.assertEquals("something was added or removed", patient.getAttributes().size(), 2);		
	}

	/**
	 * @see {@link RemoteFormEntryUtil#setPersonAttributes(Patient,Document,XPath,User)}
	 */
	@Test
	@Verifies(value = "should void previously unvoided attributes if no match exists", method = "setPersonAttributes(Patient,Document,XPath,User)")
	public void setPersonAttributes_shouldVoidPreviouslyUnvoidedAttributesIfNoMatchExists()
			throws Exception {
		// create data to -nearly- match what is in remotelyEnteredForm.xml

		Person person = new Person();
		person.setGender("M");
		
		// add two attributes of type=8 to the person

		PersonAttribute pa = new PersonAttribute();
		pa.setAttributeType(new PersonAttributeType(8));
		pa.setValue("blammo");
		pa.setVoided(false);
		person.addAttribute(pa);

		// create the actual patient

		Patient patient = new Patient(person);

		PatientIdentifier pi1 = new PatientIdentifier();
		pi1.setIdentifierType(new PatientIdentifierType(4));
		pi1.setIdentifier("1234567890");
		pi1.setLocation(new Location(1));
		pi1.setPreferred(false);
		pi1.setVoided(true);
		pi1.setVoidReason("just because");
		pi1.setVoidedBy(new User(1));
		
		PatientIdentifier pi2 = new PatientIdentifier();
		pi2.setIdentifierType(new PatientIdentifierType(1));
		pi2.setIdentifier("123456789");
		pi2.setLocation(new Location(1));
		pi2.setPreferred(true);
		pi2.setVoided(false);

		Set<PatientIdentifier> pis = new TreeSet<PatientIdentifier>();
		pis.add(pi1);
		pis.add(pi2);
		patient.setIdentifiers(pis);
		patient.addName(testName);

		patient = Context.getPatientService().savePatient(patient);

		// build all the variables that are passed to setPersonAttributes()
		DocumentBuilder db = DocumentBuilderFactory.newInstance()
				.newDocumentBuilder();
		Document doc = db
				.parse(new File(
						"src/test/resources/org/openmrs/module/remoteformentry/remotelyEnteredForm.xml"));
		XPath xp = XPathFactory.newInstance().newXPath();
		User enterer = new User(1);

		Assert.assertEquals("something went horribly wrong", patient.getAttributes().size(), 1);
		
		// call the method
		RemoteFormEntryUtil.setPersonAttributes(patient, doc, xp, enterer);

		PersonAttribute actual = patient.getAttribute(8);
		Assert.assertEquals("value was not overridden", "5", actual.getValue());
	}
}