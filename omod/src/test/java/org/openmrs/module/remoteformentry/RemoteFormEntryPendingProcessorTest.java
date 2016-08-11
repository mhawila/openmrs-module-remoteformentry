package org.openmrs.module.remoteformentry;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.Person;
import org.openmrs.PersonName;
import org.openmrs.api.context.Context;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.openmrs.test.Verifies;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.util.Set;
import java.util.TreeSet;

public class RemoteFormEntryPendingProcessorTest extends
		BaseModuleContextSensitiveTest {

	private Set<PersonName> names = new TreeSet<PersonName>();
	private PersonName testName = null;

	@Before
	public void setup() {
		testName = new PersonName();
		testName.setFamilyName("test");
		testName.setMiddleName("test");
		testName.setGivenName("test");

		names.add(testName);
	}

	/**
	 * @see {@link RemoteFormEntryPendingProcessor#getPatient(Document,XPath)}
	 */
	@Test
	@Verifies(value = "should create a Patient using an existing Person UUID", method = "getPatient(Document,XPath)")
	public void getPatient_shouldCreateAPatientUsingAnExistingPersonUUID()
			throws Exception {
		// create a person with the given UUID
		Person person = new Person();
		person.setUuid(RemoteFormEntryUtilTest.SAMPLE_XML_PERSON_UUID);
		person.setGender("M");

		person.setNames(names);
		Context.getPersonService().savePerson(person);

		// build the variables that are passed to getPatient
		DocumentBuilder db = DocumentBuilderFactory.newInstance()
				.newDocumentBuilder();
		Document doc = db
				.parse(new File(
						"src/test/resources/org/openmrs/module/remoteformentry/remotelyEnteredForm.xml"));
		XPath xp = XPathFactory.newInstance().newXPath();

		// get the Patient
		Patient patient = new RemoteFormEntryPendingProcessor().getPatient(doc,
				xp);

		// test for equality
		Integer expected = person.getId();
		Integer actual = patient.getId();
		Assert.assertEquals("did not use the existing person for the patient",
				expected, actual);
	}

	/**
	 * @see {@link RemoteFormEntryPendingProcessor#getPatient(Document,XPath)}
	 */
	@Test
	@Verifies(value = "should find an existing Patient by UUID", method = "getPatient(Document,XPath)")
	public void getPatient_shouldFindAnExistingPatientByUUID() throws Exception {
		// create a patient with the given UUID
		Patient original = new Patient();
		original.setUuid(RemoteFormEntryUtilTest.SAMPLE_XML_PERSON_UUID);
		original.setGender("M");
		PatientIdentifier pi = new PatientIdentifier();
		pi.setIdentifier("9-1");
		pi.setIdentifierType(Context.getPatientService()
				.getAllPatientIdentifierTypes().get(0));
		pi.setPreferred(true);
		original.addIdentifier(pi);

		Location location = new Location();
		location.setName("test location");
		location.setDescription("secret place");

		location = Context.getLocationService().saveLocation(location);
		pi.setLocation(location);

		original.addName(testName);
		Context.getPatientService().savePatient(original);

		// build the variables that are passed to getPatient
		DocumentBuilder db = DocumentBuilderFactory.newInstance()
				.newDocumentBuilder();
		Document doc = db
				.parse(new File(
						"src/test/resources/org/openmrs/module/remoteformentry/remotelyEnteredForm.xml"));
		XPath xp = XPathFactory.newInstance().newXPath();

		// get the Patient
		Patient patient = new RemoteFormEntryPendingProcessor().getPatient(doc,
				xp);

		// test for equality
		Integer expected = original.getId();
		Integer actual = patient.getId();
		Assert.assertEquals("did not use the existing patient", expected,
				actual);
	}

	/**
	 * @see {@link RemoteFormEntryPendingProcessor#getPatient(Document,XPath)}
	 */
	@Test
	@Verifies(value = "should not fail if no UUID is provided", method = "getPatient(Document,XPath)")
	public void getPatient_shouldNotFailIfNoUUIDIsProvided() throws Exception {
		// build the variables that are passed to getPatient
		DocumentBuilder db = DocumentBuilderFactory.newInstance()
				.newDocumentBuilder();
		Document doc = db
				.parse(new File(
						"src/test/resources/org/openmrs/module/remoteformentry/remotelyEnteredFormNoUUID.xml"));
		XPath xp = XPathFactory.newInstance().newXPath();

		// get the Patient
		new RemoteFormEntryPendingProcessor().getPatient(doc, xp);
	}

	/**
	 * @see {@link RemoteFormEntryPendingProcessor#getPatient(Document,XPath)}
	 */
	@Test
	@Ignore
	@Verifies(value = "should use the UUID provided for the new Patient", method = "getPatient(Document,XPath)")
	public void getPatient_shouldUseTheUUIDProvidedForTheNewPatient()
			throws Exception {
		// build the variables that are passed to getPatient
		DocumentBuilder db = DocumentBuilderFactory.newInstance()
				.newDocumentBuilder();
		Document doc = db
				.parse(new File(
						"src/test/resources/org/openmrs/module/remoteformentry/remotelyEnteredForm.xml"));
		XPath xp = XPathFactory.newInstance().newXPath();

		// get the Patient
		Patient patient = new RemoteFormEntryPendingProcessor().getPatient(doc,
				xp);

		// test for UUID
		Assert.assertEquals("did not use the embedded UUID for the patient",
				RemoteFormEntryUtilTest.SAMPLE_XML_PERSON_UUID, patient
						.getUuid());
	}
}