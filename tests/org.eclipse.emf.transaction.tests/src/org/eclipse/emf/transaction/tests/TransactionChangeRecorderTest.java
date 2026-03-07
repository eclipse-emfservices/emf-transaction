/**
 * Copyright (c) 2006, 2007 IBM Corporation and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   IBM - Initial API and implementation
 */
package org.eclipse.emf.transaction.tests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;

import org.eclipse.emf.common.notify.Adapter;
import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.common.notify.impl.AdapterImpl;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.util.InternalEList;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.impl.TransactionChangeRecorder;
import org.eclipse.emf.transaction.util.TransactionUtil;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the <code>TransactionChangeRecorder</code> class, specifically.
 *
 * @author Christian W. Damus (cdamus)
 */
public class TransactionChangeRecorderTest extends AbstractTest {

	private Resource rootResource;
	private Resource nestedResource1;
	private Resource nestedResource2;

	/**
	 * Tests that the change recorder did not cause the nested resources to load
	 * when loading in a read-only transaction (not creating change descriptions).
	 */
	@Test
	public void test_nestedNotLoaded_readOnlyTX() {
		startReading();
		loadRoot();

		assertTrue(rootResource.isLoaded());

		assertFalse(nestedResource1.isLoaded());
		assertFalse(nestedResource2.isLoaded());
	}

	/**
	 * Tests that the change recorder is correctly propagated to the objects in a
	 * resource when it is loaded when loading in a read-only transaction (not
	 * creating change descriptions).
	 */
	@Test
	public void test_changeRecorderPropagatedOnLoad_readOnlyTX() {
		startReading();
		loadRoot();

		TransactionChangeRecorder recorder = getRecorder(rootResource);

		EPackage pkg = findPackage("root/nested1", true);
		assertSame(recorder, getRecorder(pkg));

		Iterator<EObject> contents = ((InternalEList<EObject>) pkg.eContents()).basicIterator();

		// check that the EPackage is not yet resolved
		EObject obj = contents.next();
		assertTrue(!(obj instanceof EPackage) || obj.eIsProxy());
		obj = contents.next();
		assertTrue(!(obj instanceof EPackage) || obj.eIsProxy());

		assertFalse(nestedResource1.isLoaded());
		assertFalse(nestedResource2.isLoaded());

		// force load of the first nested resource by looking for its package
		pkg = findPackage("root/nested1/nested2", true);
		assertFalse(pkg.eIsProxy());
		assertSame(recorder, getRecorder(pkg));

		assertTrue(nestedResource1.isLoaded());
		assertFalse(nestedResource2.isLoaded());
	}

	/**
	 * Tests that the <code>setTarget()</code> method does not cause proxy
	 * resolution.
	 */
	@Test
	public void test_propagation_setTarget() {
		startReading();

		TransactionChangeRecorder recorder = getRecorder(rootResource);
		rootResource.eAdapters().remove(recorder);

		loadRoot();

		commit();

		startWriting();

		assertFalse(nestedResource1.isLoaded());
		assertFalse(nestedResource2.isLoaded());

		rootResource.eAdapters().add(recorder);

		assertFalse(nestedResource1.isLoaded());
		assertFalse(nestedResource2.isLoaded());

		// our editing domain doesn't know that this resource was loaded, so
		// we have to unload it behind its back also, otherwise tearDown()
		// will throw IllegalStateException
		rootResource.eAdapters().add(recorder);
		unloadAndRemove(rootResource);

		commit();
	}

	/**
	 * Tests that the change recorder did not cause the nested resources to load
	 * when loading in a read-write transaction (creating change descriptions).
	 */
	@Test
	public void test_nestedNotLoaded_writeTX() {
		startWriting();
		loadRoot();

		assertTrue(rootResource.isLoaded());

		assertFalse(nestedResource1.isLoaded());
		assertFalse(nestedResource2.isLoaded());
	}

	/**
	 * Tests that the change recorder is correctly propagated to the objects in a
	 * resource when it is loaded when loading in a read-write transaction (creating
	 * change descriptions).
	 */
	@Test
	public void test_changeRecorderPropagatedOnLoad_writeTX() {
		startWriting();
		loadRoot();

		TransactionChangeRecorder recorder = getRecorder(rootResource);

		EPackage pkg = findPackage("root/nested1", true);
		assertSame(recorder, getRecorder(pkg));

		Iterator<EObject> contents = ((InternalEList<EObject>) pkg.eContents()).basicIterator();

		// check that the EPackage is not yet resolved
		EObject obj = contents.next();
		assertTrue(!(obj instanceof EPackage) || obj.eIsProxy());
		obj = contents.next();
		assertTrue(!(obj instanceof EPackage) || obj.eIsProxy());

		assertFalse(nestedResource1.isLoaded());
		assertFalse(nestedResource2.isLoaded());

		// force load of the first nested resource by looking for its package
		pkg = findPackage("root/nested1/nested2", true);
		assertFalse(pkg.eIsProxy());
		assertSame(recorder, getRecorder(pkg));

		assertTrue(nestedResource1.isLoaded());
		assertFalse(nestedResource2.isLoaded());
	}

	/**
	 * Tests that disposing an editing domain (and its change recorder) removes the
	 * change recorder from all of the current contents of the resource set.
	 */
	@Test
	public void test_changeRecorderDispose_161169() {
		startWriting();
		loadRoot();

		TransactionChangeRecorder recorder = getRecorder(rootResource);

		EClass eclass = findClass("root/A", true);

		assertSame(recorder, getRecorder(eclass));

		commit();

		domain.dispose();

		try {
			// attempt to change it without a transaction. Should be allowed
			eclass.setName("NewName");
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail("Should not have asserted the transaction protocol: " + e.getLocalizedMessage());
		}
	}

	/**
	 * Tests that the change recorder is implicitly removed even from model elements
	 * that were not attached to the resource set at the time when the editing
	 * domain was disposed.
	 */
	@Test
	public void test_changeRecorderDispose_detachedElements_161169() {
		startWriting();
		loadRoot();

		TransactionChangeRecorder recorder = getRecorder(rootResource);

		EClass eclass = findClass("root/A", true);

		assertSame(recorder, getRecorder(eclass));

		// detach the EClass
		eclass.getEPackage().getEClassifiers().remove(eclass);

		commit();

		domain.dispose();

		try {
			// attempt to change it without a transaction. Should be allowed
			eclass.setName("NewName");
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail("Should not have asserted the transaction protocol: " + e.getLocalizedMessage());
		}
	}

	/**
	 * Tests the new utility for "freeing" resources from the transactional protocol
	 * of an editing domain.
	 */
	@Test
	public void test_freeDetachedResources_161169() {
		startWriting();
		loadRoot();

		TransactionChangeRecorder recorder = getRecorder(rootResource);

		EClass eclass = findClass("root/A", true);

		assertSame(recorder, getRecorder(eclass));

		commit();

		// no transaction protocol on this
		domain.getResourceSet().getResources().remove(rootResource);

		// set the resource free
		TransactionUtil.disconnectFromEditingDomain(rootResource);

		try {
			// attempt to change it without a transaction. Should be allowed
			eclass.setName("NewName");
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail("Should not have asserted the transaction protocol: " + e.getLocalizedMessage());
		}
	}

	/**
	 * Tests that the new utility frees only the proper contents of the resource.
	 */
	@Test
	public void test_freeDetachedResources_properContents_161169() {
		startWriting();
		loadRoot();

		TransactionChangeRecorder recorder = getRecorder(rootResource);

		EClass eclass = findClass("root/A", true);
		EClass nested = findClass("root/nested1/nested2/nested3/D", true);

		assertSame(recorder, getRecorder(eclass));

		commit();

		// no transaction protocol on this
		domain.getResourceSet().getResources().remove(rootResource);

		// set the root resource free
		TransactionUtil.disconnectFromEditingDomain(rootResource);

		try {
			// set the nested resource free
			TransactionUtil.disconnectFromEditingDomain(nestedResource1);

			Assert.fail("Should have thrown IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			// pass
			System.out.println("Got expected exception: " + e.getLocalizedMessage());
		}

		try {
			// modify the nested element
			nested.setName("NewName");

			Assert.fail("Should have thrown IllegalStateException");
		} catch (IllegalStateException e) {
			// pass
			System.out.println("Got expected exception: " + e.getLocalizedMessage());
		}
	}

	/**
	 * Tests the new utility for "freeing" elements from the transactional protocol
	 * of an editing domain.
	 */
	@Test
	public void test_freeDetachedElements_161169() {
		startWriting();
		loadRoot();

		TransactionChangeRecorder recorder = getRecorder(rootResource);

		EClass eclass = findClass("root/A", true);

		assertSame(recorder, getRecorder(eclass));

		// detach some ancestor, just for fun
		EObject container = eclass.eContainer();
		EcoreUtil.remove(container);

		commit();

		// set the element free
		TransactionUtil.disconnectFromEditingDomain(container);

		try {
			// attempt to change it without a transaction. Should be allowed
			eclass.setName("NewName");
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail("Should not have asserted the transaction protocol: " + e.getLocalizedMessage());
		}
	}

	/**
	 * Tests that the new utility frees only the proper contents of an element.
	 */
	@Test
	public void test_freeDetachedElements_properContents_161169() {
		startWriting();
		loadRoot();

		TransactionChangeRecorder recorder = getRecorder(rootResource);

		EClass eclass = findClass("root/A", true);
		EClass nested = findClass("root/nested1/nested2/nested3/D", true);

		assertSame(recorder, getRecorder(eclass));

		// detach some ancestor, just for fun
		EObject container = eclass.eContainer();
		EcoreUtil.remove(container);

		commit();

		try {
			// set the nested element free
			TransactionUtil.disconnectFromEditingDomain(nested);

			Assert.fail("Should have thrown IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			// pass
			System.out.println("Got expected exception: " + e.getLocalizedMessage());
		}

		try {
			// modify the nested element
			nested.setName("NewName");

			Assert.fail("Should have thrown IllegalStateException");
		} catch (IllegalStateException e) {
			// pass
			System.out.println("Got expected exception: " + e.getLocalizedMessage());
		}
	}

	/**
	 * Tests that the new utility correctly disconnects an element that was in
	 * multiple editing domains from all of them.
	 */
	@Test
	public void test_freeElements_multipleEditingDomains_161169() {
		TransactionalEditingDomain other = TransactionalEditingDomain.Factory.INSTANCE.createEditingDomain();

		startWriting();
		loadRoot();

		TransactionChangeRecorder recorder = getRecorder(rootResource);

		EClass eclass = findClass("root/A", true);

		assertSame(recorder, getRecorder(eclass));

		commit();

		// move the resources to the other domain's resource set. There is
		// no transaction protocol on this
		other.getResourceSet().getResources().add(rootResource);

		// first, attempt to set the resource free. This will find that the
		// first change recorder's editing domain could be disconnected, but
		// not the second because the resource is still in its resource set
		try {
			TransactionUtil.disconnectFromEditingDomain(rootResource);

			Assert.fail("Should have thrown IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			// pass
			System.out.println("Got expected exception: " + e.getLocalizedMessage());
		}

		// now, remove from the resource set (no transaction protocol on this)
		other.getResourceSet().getResources().remove(rootResource);

		// freeing should work now
		TransactionUtil.disconnectFromEditingDomain(rootResource);

		try {
			// attempt to change it without a transaction. Should be allowed
			eclass.setName("NewName");
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail("Should not have asserted the transaction protocol: " + e.getLocalizedMessage());
		}
	}

	@Test
	public void test_resourceLoadsWhileUnloading_189587() {
		Adapter reloader = new AdapterImpl() {

			@Override
			public void unsetTarget(Notifier oldTarget) {
				assertTrue(oldTarget instanceof EObject);
				EObject eobject = (EObject) oldTarget;
				assertTrue(eobject.eIsProxy());

				// cause the resource to re-load while it is unloading
				EcoreUtil.resolve(eobject, domain.getResourceSet());
			}
		};

		loadRoot();
		EClass eclass = findClass("root/A", true);
		eclass.eAdapters().add(reloader);

		// unload the resource
		Resource res = eclass.eResource();
		res.unload();
		assertTrue("Resource not reloaded", res.isLoaded());

		try {
			// should be allowed to modify this resource by unloading it again
			res.unload();
		} catch (IllegalStateException e) {
			Assert.fail("Should not have thrown: " + e.getLocalizedMessage());
		}
	}

	//
	// Framework methods
	//

	@Override
	protected void doSetUp() throws Exception {
		super.doSetUp();

		ResourceSet rset = domain.getResourceSet();

		try {
			rootResource = rset.getResource(
					URI.createURI(EmfTransactionTestsBundle.getEntry("/test_models/test_model.ecore").toString()),
					true);
			rootResource.setURI(URI.createPlatformResourceURI("/" + PROJECT_NAME + "/test_model.ecore", true));

			nestedResource1 = rset
					.createResource(URI.createPlatformResourceURI("/" + PROJECT_NAME + "/test_model1.ecore", true));
			nestedResource2 = rset
					.createResource(URI.createPlatformResourceURI("/" + PROJECT_NAME + "/test_model2.ecore", true));

			startWriting();

			EPackage pkg = findPackage("root/nested1/nested2", true);
			nestedResource1.getContents().add(pkg); // cross-resource-contained

			pkg = findPackage("root/nested1/nested2/nested3/nested4", true);
			nestedResource2.getContents().add(pkg); // cross-resource-contained

			commit();

			startReading();

			// save the units
			rootResource.save(Collections.EMPTY_MAP);
			nestedResource1.save(Collections.EMPTY_MAP);
			nestedResource2.save(Collections.EMPTY_MAP);

			// unload them
			rootResource.unload();
			nestedResource1.unload();
			nestedResource2.unload();

			commit();
		} catch (IOException e) {
			Assert.fail("Failed to create test model: " + e.getLocalizedMessage());
		}
	}

	@Override
	protected void doTearDown() throws Exception {
		if (rootResource != null) {
			unloadAndRemove(rootResource);
			rootResource = null;
		}

		if (nestedResource1 != null) {
			unloadAndRemove(nestedResource1);
			nestedResource1 = null;
		}

		if (nestedResource2 != null) {
			unloadAndRemove(nestedResource2);
			nestedResource2 = null;
		}

		super.doTearDown();
	}

	protected EPackage findPackage(String qname, boolean require) {
		EPackage result = (EPackage) find(rootResource, qname);

		if (require) {
			assertNotNull("Did not find package " + qname, result);
		}

		return result;
	}

	protected EClass findClass(String qname, boolean require) {
		EClass result = (EClass) find(rootResource, qname);

		if (require) {
			assertNotNull("Did not find class " + qname, result);
		}

		return result;
	}

	/**
	 * Gets the name of an Ecore object.
	 * 
	 * @param object the object
	 * @return its name
	 */
	@Override
	protected String getName(EObject object) {
		if (object instanceof ENamedElement) {
			return ((ENamedElement) object).getName();
		}

		return super.getName(object);
	}

	protected void loadRoot() {
		try {
			rootResource.load(Collections.EMPTY_MAP);
		} catch (IOException e) {
			e.printStackTrace();
			Assert.fail("Failed to load root resource: " + e.getLocalizedMessage());
		}
	}

	protected TransactionChangeRecorder getRecorder(Notifier notifier) {
		TransactionChangeRecorder result = null;

		for (Object next : notifier.eAdapters()) {
			if (next instanceof TransactionChangeRecorder) {
				result = (TransactionChangeRecorder) next;
				break;
			}
		}

		assertNotNull("Did not find change recorder", result);

		return result;
	}
}
