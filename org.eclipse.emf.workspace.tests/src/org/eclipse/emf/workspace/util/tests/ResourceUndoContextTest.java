/**
 * <copyright>
 *
 * Copyright (c) 2005, 2009 IBM Corporation, Christian W. Damus, and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   IBM - Initial API and implementation
 *   Christian W. Damus - Bug 264220
 *
 * </copyright>
 *
 * $Id: ResourceUndoContextTest.java,v 1.4.2.1 2009/02/10 04:17:36 cdamus Exp $
 */
package org.eclipse.emf.workspace.util.tests;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.eclipse.core.commands.operations.DefaultOperationHistory;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EFactory;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EContentAdapter;
import org.eclipse.emf.examples.extlibrary.Book;
import org.eclipse.emf.examples.extlibrary.BookOnTape;
import org.eclipse.emf.examples.extlibrary.EXTLibraryFactory;
import org.eclipse.emf.examples.extlibrary.Employee;
import org.eclipse.emf.examples.extlibrary.Library;
import org.eclipse.emf.examples.extlibrary.Writer;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.workspace.ResourceUndoContext;
import org.eclipse.emf.workspace.WorkspaceEditingDomainFactory;
import org.eclipse.emf.workspace.tests.fixtures.TestPackageBuilder;

/**
 * Tests the {@link ResourceUndoContext} class.
 *
 * @author Christian W. Damus (cdamus)
 */
public class ResourceUndoContextTest extends TestCase {
	
	private ResourceUndoContext ctx1;
	private ResourceUndoContext ctx2;
	private ResourceUndoContext ctx3;
	
	private Resource res1;
	private Resource res2;
	private Resource res3;
	
	private Listener listener;
	
	private TestPackageBuilder packageBuilder;
	
	public ResourceUndoContextTest(String name) {
		super(name);
	}

	public static Test suite() {
		return new TestSuite(ResourceUndoContextTest.class, "Resource Undo Context Tests"); //$NON-NLS-1$
	}

	/**
	 * Tests the matching of contexts.
	 */
	public void test_matches() {
		assertFalse(ctx1.matches(ctx3));
		assertTrue(ctx2.matches(ctx3));
	}
	
	/**
	 * Tests the analysis of an attribute.
	 */
	public void test_getAffectedResources_attribute() {
		Library library = EXTLibraryFactory.eINSTANCE.createLibrary();
		res1.getContents().add(library);
		
		// forget the events so far
		listener.notifications.clear();
		
		library.setName("Foo"); //$NON-NLS-1$
		
		assertFalse(listener.notifications.isEmpty());
		
		Set<Resource> affected = ResourceUndoContext.getAffectedResources(
				listener.notifications);
		
		assertEquals(Collections.singleton(res1), affected);
	}
	
	/**
	 * Tests the analysis of a unidirectional reference within the same resource.
	 */
	public void test_getAffectedResources_localRef() {
		Library library = EXTLibraryFactory.eINSTANCE.createLibrary();
		res1.getContents().add(library);
		
		BookOnTape book = EXTLibraryFactory.eINSTANCE.createBookOnTape();
		library.getStock().add(book);
		
		Employee person = EXTLibraryFactory.eINSTANCE.createEmployee();
		library.getEmployees().add(person);
		
		// forget the events so far
		listener.notifications.clear();
		
		book.setReader(person);
		
		assertFalse(listener.notifications.isEmpty());
		
		Set<Resource> affected = ResourceUndoContext.getAffectedResources(
				listener.notifications);
		
		assertEquals(Collections.singleton(res1), affected);
	}
	
	/**
	 * Tests the analysis of a bidirectional reference across resources.
	 */
	public void test_getAffectedResources_remoteRef_bidirectional() {
		Library library1 = EXTLibraryFactory.eINSTANCE.createLibrary();
		res1.getContents().add(library1);
		
		Library library2 = EXTLibraryFactory.eINSTANCE.createLibrary();
		res2.getContents().add(library2);
		
		Book book = EXTLibraryFactory.eINSTANCE.createBook();
		library1.getStock().add(book);
		
		Writer writer = EXTLibraryFactory.eINSTANCE.createWriter();
		library2.getWriters().add(writer);
		
		// forget the events so far
		listener.notifications.clear();
		
		book.setAuthor(writer);
		
		assertFalse(listener.notifications.isEmpty());
		
		Set<Resource> affected = ResourceUndoContext.getAffectedResources(
				listener.notifications);
		
		Set<Resource> expected = new java.util.HashSet<Resource>();
		expected.add(res1);
		expected.add(res2);
		
		assertEquals(expected, affected);
	}
	
	/**
	 * Tests the analysis of a unidirectional reference across resources.
	 */
	public void test_getAffectedResources_remoteRef_unidirectional() {
		Library library1 = EXTLibraryFactory.eINSTANCE.createLibrary();
		res1.getContents().add(library1);
		
		Library library2 = EXTLibraryFactory.eINSTANCE.createLibrary();
		res2.getContents().add(library2);
		
		BookOnTape book = EXTLibraryFactory.eINSTANCE.createBookOnTape();
		library1.getStock().add(book);
		
		Employee person = EXTLibraryFactory.eINSTANCE.createEmployee();
		library2.getEmployees().add(person);
		
		// forget the events so far
		listener.notifications.clear();
		
		book.setReader(person);
		
		assertFalse(listener.notifications.isEmpty());
		
		Set<Resource> affected = ResourceUndoContext.getAffectedResources(
				listener.notifications);
		
		Set<Resource> expected = new java.util.HashSet<Resource>();
		expected.add(res1);
		expected.add(res2);
		
		assertEquals(expected, affected);
	}
	
	/**
	 * Tests the analysis of notifications from detached objects, to avoid
	 * adding resource contexts with null resources.
	 */
	public void test_getAffectedResources_deletedElement_126113() {
		Library library1 = EXTLibraryFactory.eINSTANCE.createLibrary();
		res1.getContents().add(library1);
		
		Library library2 = EXTLibraryFactory.eINSTANCE.createLibrary();
		res2.getContents().add(library2);
		
		BookOnTape book = EXTLibraryFactory.eINSTANCE.createBookOnTape();
		library1.getStock().add(book);
		
		Employee person = EXTLibraryFactory.eINSTANCE.createEmployee();
		library2.getEmployees().add(person);
		
		book.setReader(person);
		
		// forget the events so far
		listener.notifications.clear();

		library2.getEmployees().remove(person);
		book.setReader(null);   // this caused the null resource context
		
		assertFalse(listener.notifications.isEmpty());
		
		Set<Resource> affected = ResourceUndoContext.getAffectedResources(
				listener.notifications);
		
		Set<Resource> expected = new java.util.HashSet<Resource>();
		expected.add(res1);
		expected.add(res2);
		
		assertEquals(expected, affected);
		assertFalse(affected.contains(null));
	}
	
	public void test_unsettableManyReference_264220() {
		EFactory factory = packageBuilder.getPackage().getEFactoryInstance();
		
		EObject anA = factory.create(packageBuilder.getA());
		EObject aB = factory.create(packageBuilder.getB());
		EObject anotherB = factory.create(packageBuilder.getB());
		
		anA.eAdapters().add(listener);
		res1.getContents().add(aB);
		res2.getContents().add(anotherB);
		
		// do some linking of objects
		@SuppressWarnings("unchecked")
		EList<EObject> bs = (EList<EObject>) anA.eGet(packageBuilder.getA_b());
		bs.add(aB);
		bs.add(anotherB);
		
		// start over with the event gathering, on a clean slate
		listener.notifications.clear();

		// now, unset the unsettable reference
		anA.eUnset(packageBuilder.getA_b());
		
		try {
			Set<Resource> expectedResources = new java.util.HashSet<Resource>();
			expectedResources.add(res1);
			expectedResources.add(res2);
			assertEquals(expectedResources, ResourceUndoContext
				.getAffectedResources(listener.notifications));
		} catch (ClassCastException e) {
			fail("Should not get CCE in the resource undo-context policy"); //$NON-NLS-1$
		}
	}
	
	//
	// Fixture methods
	//
	
	@Override
	protected void setUp()
		throws Exception {
		
		TransactionalEditingDomain domain =
			WorkspaceEditingDomainFactory.INSTANCE.createEditingDomain(
				new DefaultOperationHistory());
		
		res1 = new ResourceImpl();
		res2 = new ResourceImpl();
		res3 = new ResourceImpl();
		
		ctx1 = new ResourceUndoContext(domain, res1);
		ctx2 = new ResourceUndoContext(domain, res2);
		ctx3 = new ResourceUndoContext(domain, res2); // not res3
		
		ResourceSet rset = new ResourceSetImpl();
		rset.getResources().add(res1);
		rset.getResources().add(res2);
		rset.getResources().add(res3);
		
		listener = new Listener();
		rset.eAdapters().add(listener);
		
		packageBuilder = new TestPackageBuilder();
	}
	
	@Override
	protected void tearDown()
		throws Exception {
		
		packageBuilder.dispose();
		
		listener = null;
		
		res1 = null;
		res2 = null;
		res3 = null;
		
		ctx1 = null;
		ctx2 = null;
		ctx3 = null;
	}
	
	/**
	 * Records a failure due to an exception that should not have been thrown.
	 * 
	 * @param e the exception
	 */
	protected void fail(Exception e) {
		e.printStackTrace();
		fail("Should not have thrown: " + e.getLocalizedMessage()); //$NON-NLS-1$
	}
	
	private static class Listener extends EContentAdapter {
		final List<Notification> notifications = new java.util.ArrayList<Notification>();
		
		@Override
		public void notifyChanged(Notification notification) {
			notifications.add(notification);
			
			super.notifyChanged(notification);
		}
	}
}
