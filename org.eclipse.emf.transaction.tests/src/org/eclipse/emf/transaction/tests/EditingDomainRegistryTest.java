/**
 * <copyright>
 *
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   IBM - Initial API and implementation
 *
 * </copyright>
 *
 * $Id: EditingDomainRegistryTest.java,v 1.5 2007/06/07 14:26:17 cdamus Exp $
 */
package org.eclipse.emf.transaction.tests;

import java.lang.ref.WeakReference;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.tests.fixtures.TestEditingDomain;
import org.eclipse.emf.transaction.tests.fixtures.TestListener;


/**
 * Tests the editing domain registry and the associated extension point.
 *
 * @author Christian W. Damus (cdamus)
 */
public class EditingDomainRegistryTest extends AbstractTest {
	private static final String TEST_DOMAIN1 = "org.eclipse.emf.transaction.tests.TestDomain1"; //$NON-NLS-1$
	private static final String TEST_DOMAIN2 = "org.eclipse.emf.transaction.tests.TestDomain2"; //$NON-NLS-1$
	private static final String TEST_DOMAIN3 = "org.eclipse.emf.transaction.tests.TestDomain3"; //$NON-NLS-1$
	private static final String TEST_DOMAIN4 = "org.eclipse.emf.transaction.tests.TestDomain4"; //$NON-NLS-1$
	
	public EditingDomainRegistryTest(String name) {
		super(name);
	}

	public static Test suite() {
		return new TestSuite(EditingDomainRegistryTest.class, "Editing Domain Registry Tests"); //$NON-NLS-1$
	}

	/**
	 * Tests dynamically adding and removing domains in the registry.
	 */
	public void test_dynamicAddRemove() {
		assertNull(domain.getID());
		
		TransactionalEditingDomain.Registry.INSTANCE.add(TEST_DOMAIN2, domain);
		
		// registry set the ID
		assertEquals(TEST_DOMAIN2, domain.getID());
		
		assertSame(domain, TransactionalEditingDomain.Registry.INSTANCE.getEditingDomain(
				TEST_DOMAIN2));
		
		// remove the domain
		assertSame(domain, TransactionalEditingDomain.Registry.INSTANCE.remove(
				TEST_DOMAIN2));
		
		// no longer present
		assertNull(TransactionalEditingDomain.Registry.INSTANCE.getEditingDomain(TEST_DOMAIN2));
		
		// still have our ID
		assertEquals(TEST_DOMAIN2, domain.getID());
	}
	
	/**
	 * Tests the static registration of editing domains on the extension point.
	 */
	public void test_staticRegistration() {
		// check initial conditions for this test
		assertEquals(0, TestEditingDomain.instanceCount);
		
		TransactionalEditingDomain registered = TransactionalEditingDomain.Registry.INSTANCE.getEditingDomain(
				TEST_DOMAIN1);
		
		assertTrue(registered instanceof TestEditingDomain); // our factory was used
		assertEquals(TEST_DOMAIN1, registered.getID());      // ID matches
		assertEquals(1, TestEditingDomain.instanceCount);    // one instance
		
		// cannot remove statically registered domains
		try {
			TransactionalEditingDomain.Registry.INSTANCE.remove(TEST_DOMAIN1);
			fail("Should have thrown IllegalArgumentException"); //$NON-NLS-1$
		} catch (IllegalArgumentException e) {
			trace("Got expected exception: " + e.getLocalizedMessage()); //$NON-NLS-1$
		}
		
		// check that it is not recreated by another get call
		assertSame(
				registered,
				TransactionalEditingDomain.Registry.INSTANCE.getEditingDomain(TEST_DOMAIN1));
		assertEquals(1, TestEditingDomain.instanceCount);    // no new instance
	}
	
	/**
	 * Tests the replacement of a domain under an ID with another.
	 */
	public void test_replaceDomain() {
		TransactionalEditingDomain domain1 = TransactionalEditingDomain.Registry.INSTANCE.getEditingDomain(
				"org.eclipse.emf.transaction.tests.foo"); //$NON-NLS-1$
		assertNull(domain1);
		
		domain1 = new TestEditingDomain.FactoryImpl().createEditingDomain();
		assertNotNull(domain1);
		TransactionalEditingDomain.Registry.INSTANCE.add(
				"org.eclipse.emf.transaction.tests.foo", //$NON-NLS-1$
				domain1);
		
		// check that we successfully registered domain1
		assertSame(
				domain1,
				TransactionalEditingDomain.Registry.INSTANCE.getEditingDomain(
						"org.eclipse.emf.transaction.tests.foo")); //$NON-NLS-1$
		
		// create another domain and register it under the same ID
		TransactionalEditingDomain domain2 = new TestEditingDomain.FactoryImpl().createEditingDomain();
		TransactionalEditingDomain.Registry.INSTANCE.add(
				"org.eclipse.emf.transaction.tests.foo", //$NON-NLS-1$
				domain2);
		
		// check that we successfully replaced domain1
		assertSame(
				domain2,
				TransactionalEditingDomain.Registry.INSTANCE.getEditingDomain(
						"org.eclipse.emf.transaction.tests.foo")); //$NON-NLS-1$
		
		TransactionalEditingDomain.Registry.INSTANCE.remove("org.eclipse.emf.transaction.tests.foo"); //$NON-NLS-1$
	}
	
	/**
	 * Tests the automatic re-registration of a domain when its ID is changed.
	 */
	public void test_changeDomainId() {
		TransactionalEditingDomain domain1 = new TestEditingDomain.FactoryImpl().createEditingDomain();
		assertNotNull(domain1);
		domain1.setID("org.eclipse.emf.transaction.tests.foo"); //$NON-NLS-1$
		
		// not yet registered
		assertNull(TransactionalEditingDomain.Registry.INSTANCE.getEditingDomain(
				"org.eclipse.emf.transaction.tests.foo")); //$NON-NLS-1$
		assertNull(TransactionalEditingDomain.Registry.INSTANCE.getEditingDomain(
				"org.eclipse.emf.transaction.tests.bar")); //$NON-NLS-1$
		
		// register it
		TransactionalEditingDomain.Registry.INSTANCE.add(
				"org.eclipse.emf.transaction.tests.foo", //$NON-NLS-1$
				domain1);
		assertNotNull(TransactionalEditingDomain.Registry.INSTANCE.getEditingDomain(
				"org.eclipse.emf.transaction.tests.foo")); //$NON-NLS-1$
		assertNull(TransactionalEditingDomain.Registry.INSTANCE.getEditingDomain(
				"org.eclipse.emf.transaction.tests.bar")); //$NON-NLS-1$
		
		// change the ID
		domain1.setID("org.eclipse.emf.transaction.tests.bar"); //$NON-NLS-1$
		
		// automatically re-registered
		assertNull(TransactionalEditingDomain.Registry.INSTANCE.getEditingDomain(
				"org.eclipse.emf.transaction.tests.foo")); //$NON-NLS-1$
		assertNotNull(TransactionalEditingDomain.Registry.INSTANCE.getEditingDomain(
				"org.eclipse.emf.transaction.tests.bar")); //$NON-NLS-1$
		
		// remove
		TransactionalEditingDomain.Registry.INSTANCE.remove(domain1.getID());
		assertNull(TransactionalEditingDomain.Registry.INSTANCE.getEditingDomain(
				"org.eclipse.emf.transaction.tests.foo")); //$NON-NLS-1$
		assertNull(TransactionalEditingDomain.Registry.INSTANCE.getEditingDomain(
				"org.eclipse.emf.transaction.tests.bar")); //$NON-NLS-1$
		
		// change the ID back
		domain1.setID("org.eclipse.emf.transaction.tests.foo"); //$NON-NLS-1$
		
		// didn't re-register itself this time
		assertNull(TransactionalEditingDomain.Registry.INSTANCE.getEditingDomain(
				"org.eclipse.emf.transaction.tests.bar")); //$NON-NLS-1$
		assertNull(TransactionalEditingDomain.Registry.INSTANCE.getEditingDomain(
				"org.eclipse.emf.transaction.tests.foo")); //$NON-NLS-1$
	}
	
	/**
	 * Tests the attachment and detachment of registered listeners to editing domains.
	 */
	public void test_listenerRegistration_singleDomain_multipleListeners() {
		TransactionalEditingDomain domain3 = TransactionalEditingDomain.Registry.INSTANCE.getEditingDomain(
				TEST_DOMAIN3);
		assertNull(domain3);
		
		domain3 = new TestEditingDomain.FactoryImpl().createEditingDomain();
		assertNotNull(domain3);
		TransactionalEditingDomain.Registry.INSTANCE.add(TEST_DOMAIN3, domain3);
		
		// look for our two test listeners that are registered on the TestDomain3
		assertNotNull(TestListener1.getInstance());
		assertNotNull(TestListener2.getInstance());
		
		final TransactionalEditingDomain finalDomain3 = domain3;
		try {
			domain3.runExclusive(new Runnable() {
				public void run() {
					finalDomain3.getResourceSet().createResource(
							URI.createFileURI("/tmp/dummy.extlibrary")); //$NON-NLS-1$
				}});
		} catch (Exception e) {
			fail(e);
		}
		
		// should have gotten events from the read transaction, above
		assertNotNull(TestListener1.getInstance().postcommit);
		List notifications = TestListener1.getInstance().postcommit.getNotifications();
		assertFalse(notifications == null);
		assertEquals(1, notifications.size());
		Notification notification = (Notification) notifications.get(0);
		assertSame(domain3.getResourceSet(), notification.getNotifier());
		assertEquals(ResourceSet.RESOURCE_SET__RESOURCES, notification.getFeatureID(null));
		assertEquals(Notification.ADD, notification.getEventType());
		
		assertNotNull(TestListener2.getInstance().postcommit);
		notifications = TestListener2.getInstance().postcommit.getNotifications();
		assertFalse(notifications == null);
		assertEquals(1, notifications.size());
		assertSame(notification, notifications.get(0)); // same notification as other
		
		TransactionalEditingDomain.Registry.INSTANCE.remove(TEST_DOMAIN3);
		
		Runtime.getRuntime().gc();  // try to collect garbage
		try {
			Thread.sleep(2000);         // give garbage collector some time
		} catch (InterruptedException e) {
			// ignore
		}
		Runtime.getRuntime().gc();  // try to collect garbage
		
		// our two listeners should have been reclaimed because they are
		//    removed from the editing domain
		assertNull(TestListener1.getInstance());
		assertNull(TestListener2.getInstance());
	}
	
	/**
	 * Tests the attachment and detachment of registered listeners to editing domains.
	 */
	public void test_listenerRegistration_multiDomains_singleListener() {
		TransactionalEditingDomain domain3 = TransactionalEditingDomain.Registry.INSTANCE.getEditingDomain(
				TEST_DOMAIN3);
		assertNull(domain3);
		
		domain3 = new TestEditingDomain.FactoryImpl().createEditingDomain();
		assertNotNull(domain3);
		TransactionalEditingDomain.Registry.INSTANCE.add(TEST_DOMAIN3, domain3);
		
		TransactionalEditingDomain domain4 = TransactionalEditingDomain.Registry.INSTANCE.getEditingDomain(
				TEST_DOMAIN4);
		assertNull(domain4);
		
		domain4 = new TestEditingDomain.FactoryImpl().createEditingDomain();
		assertNotNull(domain4);
		TransactionalEditingDomain.Registry.INSTANCE.add(TEST_DOMAIN4, domain4);
		
		// look for our test listener that is registered on both domains
		assertNotNull(TestListener2.getInstance());
		
		final TransactionalEditingDomain finalDomain3 = domain3;
		try {
			domain3.runExclusive(new Runnable() {
				public void run() {
					finalDomain3.getResourceSet().createResource(
							URI.createFileURI("/tmp/dummy.extlibrary")); //$NON-NLS-1$
				}});
		} catch (Exception e) {
			fail(e);
		}
		
		// should have gotten events from the read transaction, above
		assertNotNull(TestListener2.getInstance().postcommit);
		List notifications = TestListener2.getInstance().postcommitNotifications;
		assertFalse(notifications == null);
		assertEquals(1, notifications.size());
		Notification notification = (Notification) notifications.get(0);
		assertSame(domain3.getResourceSet(), notification.getNotifier());
		assertEquals(ResourceSet.RESOURCE_SET__RESOURCES, notification.getFeatureID(null));
		assertEquals(Notification.ADD, notification.getEventType());

		TestListener2.getInstance().reset();  // clear the listener for next step
		
		final TransactionalEditingDomain finalDomain4 = domain4;
		try {
			domain4.runExclusive(new Runnable() {
				public void run() {
					finalDomain4.getResourceSet().createResource(
							URI.createFileURI("/tmp/dummy.extlibrary")); //$NON-NLS-1$
				}});
		} catch (Exception e) {
			fail(e);
		}
		
		// should have gotten events from this read transaction, too
		assertNotNull(TestListener2.getInstance().postcommit);
		notifications = TestListener2.getInstance().postcommitNotifications;
		assertFalse(notifications == null);
		assertEquals(1, notifications.size());
		notification = (Notification) notifications.get(0);
		assertSame(domain4.getResourceSet(), notification.getNotifier());
		assertEquals(ResourceSet.RESOURCE_SET__RESOURCES, notification.getFeatureID(null));
		assertEquals(Notification.ADD, notification.getEventType());
		
		TransactionalEditingDomain.Registry.INSTANCE.remove(TEST_DOMAIN3);
		TransactionalEditingDomain.Registry.INSTANCE.remove(TEST_DOMAIN4);
		
		Runtime.getRuntime().gc();  // try to collect garbage
		try {
			Thread.sleep(2000);         // give garbage collector some time
		} catch (InterruptedException e) {
			// ignore
		}
		Runtime.getRuntime().gc();  // try to collect garbage
		
		// our two listener should have been reclaimed because it is
		//    removed from both editing domains
		assertNull(TestListener2.getInstance());
	}
	
	/**
	 * Tests the attachment and detachment of registered listeners to editing domains.
	 */
	public void test_listenerRegistration_universalListener() {
		TransactionalEditingDomain domain3 = TransactionalEditingDomain.Registry.INSTANCE.getEditingDomain(
				TEST_DOMAIN3);
		assertNull(domain3);
		
		domain3 = new TestEditingDomain.FactoryImpl().createEditingDomain();
		assertNotNull(domain3);
		TransactionalEditingDomain.Registry.INSTANCE.add(TEST_DOMAIN3, domain3);
		
		TransactionalEditingDomain domain4 = TransactionalEditingDomain.Registry.INSTANCE.getEditingDomain(
				TEST_DOMAIN4);
		assertNull(domain4);
		
		domain4 = new TestEditingDomain.FactoryImpl().createEditingDomain();
		assertNotNull(domain4);
		TransactionalEditingDomain.Registry.INSTANCE.add(TEST_DOMAIN4, domain4);
		
		// look for our test listener that is registered on both domains
		assertNotNull(TestListener3.getInstance());
		
		final TransactionalEditingDomain finalDomain3 = domain3;
		try {
			domain3.runExclusive(new Runnable() {
				public void run() {
					finalDomain3.getResourceSet().createResource(
							URI.createFileURI("/tmp/dummy.extlibrary")); //$NON-NLS-1$
				}});
		} catch (Exception e) {
			fail(e);
		}
		
		// should have gotten events from the read transaction, above
		assertNotNull(TestListener3.getInstance().postcommit);
		List notifications = TestListener3.getInstance().postcommitNotifications;
		assertFalse(notifications == null);
		assertEquals(1, notifications.size());
		Notification notification = (Notification) notifications.get(0);
		assertSame(domain3.getResourceSet(), notification.getNotifier());
		assertEquals(ResourceSet.RESOURCE_SET__RESOURCES, notification.getFeatureID(null));
		assertEquals(Notification.ADD, notification.getEventType());

		TestListener3.getInstance().reset();  // clear the listener for next step
		
		final TransactionalEditingDomain finalDomain4 = domain4;
		try {
			domain4.runExclusive(new Runnable() {
				public void run() {
					finalDomain4.getResourceSet().createResource(
							URI.createFileURI("/tmp/dummy.extlibrary")); //$NON-NLS-1$
				}});
		} catch (Exception e) {
			fail(e);
		}
		
		// should have gotten events from this read transaction, too
		assertNotNull(TestListener3.getInstance().postcommit);
		notifications = TestListener3.getInstance().postcommitNotifications;
		assertFalse(notifications == null);
		assertEquals(1, notifications.size());
		notification = (Notification) notifications.get(0);
		assertSame(domain4.getResourceSet(), notification.getNotifier());
		assertEquals(ResourceSet.RESOURCE_SET__RESOURCES, notification.getFeatureID(null));
		assertEquals(Notification.ADD, notification.getEventType());
		
		TransactionalEditingDomain.Registry.INSTANCE.remove(TEST_DOMAIN3);
		TransactionalEditingDomain.Registry.INSTANCE.remove(TEST_DOMAIN4);
		
		// can't test for clean removal because it is also on TestDomain1 (being
		//   a universal listener) and TestDomain1 cannot be removed.  It's not
		//   necessary to test this again, though, anyway
	}
	
	/**
	 * Tests that we can omit the factory class in the editing domain
	 * registration to use the default shared factory instance.
	 */
	public void test_registerDefaultFactory_136674() {
		TransactionalEditingDomain defaultDomain =
			TransactionalEditingDomain.Registry.INSTANCE.getEditingDomain(
				"org.eclipse.emf.transaction.tests.TestDefaultFactoryDomain1"); //$NON-NLS-1$
		assertNotNull(defaultDomain);
	}
	
	//
	// Fixtures
	//
	
	/** Test listener registered against TestDomain3. */
	public static class TestListener1 extends TestListener {
		private static WeakReference instance;
		
		public TestListener1() {
			instance = new WeakReference(this);
		}
		
		public static TestListener1 getInstance() {
			return instance == null ? null : (TestListener1) instance.get();
		}
	}
	
	/** Test listener registered against TestDomain3. */
	public static class TestListener2 extends TestListener {
		private static WeakReference instance;
		
		public TestListener2() {
			instance = new WeakReference(this);
		}
		
		public static TestListener2 getInstance() {
			return instance == null ? null : (TestListener2) instance.get();
		}
	}
	
	/** Test listener registered against all domains. */
	public static class TestListener3 extends TestListener {
		private static WeakReference instance;
		
		public TestListener3() {
			instance = new WeakReference(this);
		}
		
		public static TestListener3 getInstance() {
			return instance == null ? null : (TestListener3) instance.get();
		}
	}
	
}
