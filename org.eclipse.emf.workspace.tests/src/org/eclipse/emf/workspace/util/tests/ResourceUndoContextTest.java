/**
 * <copyright>
 *
 * Copyright (c) 2005, 2006 IBM Corporation and others.
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
 * $Id: ResourceUndoContextTest.java,v 1.3 2006/02/01 23:12:10 cdamus Exp $
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
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EContentAdapter;
import org.eclipse.emf.examples.extlibrary.Book;
import org.eclipse.emf.examples.extlibrary.BookOnTape;
import org.eclipse.emf.examples.extlibrary.EXTLibraryFactory;
import org.eclipse.emf.examples.extlibrary.Library;
import org.eclipse.emf.examples.extlibrary.Person;
import org.eclipse.emf.examples.extlibrary.Writer;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.workspace.ResourceUndoContext;
import org.eclipse.emf.workspace.WorkspaceEditingDomainFactory;

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
		
		Set affected = ResourceUndoContext.getAffectedResources(
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
		
		Person person = EXTLibraryFactory.eINSTANCE.createEmployee();
		library.getEmployees().add(person);
		
		// forget the events so far
		listener.notifications.clear();
		
		book.setReader(person);
		
		assertFalse(listener.notifications.isEmpty());
		
		Set affected = ResourceUndoContext.getAffectedResources(
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
		
		Set affected = ResourceUndoContext.getAffectedResources(
				listener.notifications);
		
		Set expected = new java.util.HashSet();
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
		
		Person person = EXTLibraryFactory.eINSTANCE.createEmployee();
		library2.getEmployees().add(person);
		
		// forget the events so far
		listener.notifications.clear();
		
		book.setReader(person);
		
		assertFalse(listener.notifications.isEmpty());
		
		Set affected = ResourceUndoContext.getAffectedResources(
				listener.notifications);
		
		Set expected = new java.util.HashSet();
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
		
		Person person = EXTLibraryFactory.eINSTANCE.createEmployee();
		library2.getEmployees().add(person);
		
		book.setReader(person);
		
		// forget the events so far
		listener.notifications.clear();

		library2.getEmployees().remove(person);
		book.setReader(null);   // this caused the null resource context
		
		assertFalse(listener.notifications.isEmpty());
		
		Set affected = ResourceUndoContext.getAffectedResources(
				listener.notifications);
		
		Set expected = new java.util.HashSet();
		expected.add(res1);
		expected.add(res2);
		
		assertEquals(expected, affected);
		assertFalse(affected.contains(null));
	}
	
	//
	// Fixture methods
	//
	
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
	}
	
	protected void tearDown()
		throws Exception {
		
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
		final List notifications = new java.util.ArrayList();
		
		public void notifyChanged(Notification notification) {
			notifications.add(notification);
			
			super.notifyChanged(notification);
		}
	}
}
