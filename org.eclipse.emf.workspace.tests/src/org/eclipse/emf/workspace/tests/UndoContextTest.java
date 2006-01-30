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
 * $Id: UndoContextTest.java,v 1.1 2006/01/30 16:26:01 cdamus Exp $
 */
package org.eclipse.emf.workspace.tests;

import java.util.Collections;
import java.util.Set;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.examples.extlibrary.Book;
import org.eclipse.emf.examples.extlibrary.EXTLibraryFactory;
import org.eclipse.emf.examples.extlibrary.Library;
import org.eclipse.emf.examples.extlibrary.Writer;
import org.eclipse.emf.workspace.ResourceUndoContext;
import org.eclipse.emf.workspace.tests.fixtures.TestOperation;
import org.eclipse.emf.workspace.tests.fixtures.TestUndoContext;


/**
 * Tests the management of {@link ResourceUndoContext}s.
 *
 * @author Christian W. Damus (cdamus)
 */
public class UndoContextTest extends AbstractTest {

	public UndoContextTest(String name) {
		super(name);
	}
	
	public static Test suite() {
		return new TestSuite(UndoContextTest.class, "Undo Context Tests"); //$NON-NLS-1$
	}
	
	/**
	 * Tests determination of undo context from local changes (in attributes and
	 * references).
	 */
	public void test_localChanges() {
		startReading();
		
		final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
		assertNotNull(book);
		
		final String newTitle = "New Title"; //$NON-NLS-1$
		final Writer newAuthor = (Writer) find("root/level1/Level1 Writer"); //$NON-NLS-1$
		assertNotNull(newAuthor);
		
		commit();
		
		IUndoContext ctx = new TestUndoContext();
		
		IUndoableOperation oper = new TestOperation(domain) {
			protected void doExecute() {
				book.setTitle(newTitle);
				newAuthor.getBooks().add(book);
			}};
		
		try {
			oper.addContext(ctx);
			history.execute(oper, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		Set affected = ResourceUndoContext.getAffectedResources(oper);
		
		assertNotNull(affected);
		assertEquals(Collections.singleton(testResource), affected);
	}
	
	/**
	 * Tests determination of undo context from remote changes (in attributes and
	 * references).
	 */
	public void test_remoteChanges() {
		Resource res2 = new ResourceImpl();
		Library lib2 = EXTLibraryFactory.eINSTANCE.createLibrary();
		res2.getContents().add(lib2);
		final Writer newAuthor = EXTLibraryFactory.eINSTANCE.createWriter();
		lib2.getWriters().add(newAuthor);
		
		startReading();
		
		// add this other resource to my resource set
		domain.getResourceSet().getResources().add(res2);
		
		final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
		assertNotNull(book);
		
		final String newTitle = "New Title"; //$NON-NLS-1$
		
		commit();
		
		IUndoContext ctx = new TestUndoContext();
		
		IUndoableOperation oper = new TestOperation(domain) {
			protected void doExecute() {
				book.setTitle(newTitle);
				newAuthor.getBooks().add(book);
			}};
		
		try {
			oper.addContext(ctx);
			history.execute(oper, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		Set affected = ResourceUndoContext.getAffectedResources(oper);
		
		assertNotNull(affected);
		
		Set expected = new java.util.HashSet();
		expected.add(testResource);
		expected.add(res2);
		
		assertEquals(expected, affected);
	}
}
