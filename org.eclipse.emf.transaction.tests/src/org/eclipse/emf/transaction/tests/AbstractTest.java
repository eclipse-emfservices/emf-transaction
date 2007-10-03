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
 * $Id: AbstractTest.java,v 1.9 2007/10/03 20:17:27 cdamus Exp $
 */
package org.eclipse.emf.transaction.tests;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.examples.extlibrary.AudioVisualItem;
import org.eclipse.emf.examples.extlibrary.Book;
import org.eclipse.emf.examples.extlibrary.Library;
import org.eclipse.emf.examples.extlibrary.Periodical;
import org.eclipse.emf.examples.extlibrary.Person;
import org.eclipse.emf.examples.extlibrary.Writer;
import org.eclipse.emf.examples.extlibrary.util.EXTLibrarySwitch;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.TransactionalCommandStack;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.impl.InternalTransaction;
import org.eclipse.emf.transaction.impl.InternalTransactionalEditingDomain;
import org.eclipse.emf.validation.model.IConstraintStatus;
import org.osgi.framework.Bundle;

/**
 * Abstract test framework for the transaction unit tests.
 * 
 * @author Christian W. Damus (cdamus)
 */
public class AbstractTest
	extends TestCase {
	
	public static final boolean DEBUGGING = TestsPlugin.instance.isDebugging();
	
	static final Bundle EmfTransactionTestsBundle =	TestsPlugin.instance.getBundle();

	protected IProject project;
	protected IFile file;
	protected TransactionalEditingDomain domain;
	protected Resource testResource;
	protected Library root;
	
	protected static final String PROJECT_NAME = "emftxtests"; //$NON-NLS-1$
	protected static final String RESOURCE_NAME = "/" + PROJECT_NAME + "/testres.extlibrary";  //$NON-NLS-1$//$NON-NLS-2$

	private final List transactionStack = new java.util.ArrayList();
    
    private List tearDownActions;
	
	public AbstractTest() {
		super();
	}
	
	public AbstractTest(String name) {
		super(name);
	}
	
	//
	// Test configuration methods
	//
	
	protected final void setUp()
		throws Exception {
		
		trace("===> Begin : " + getName()); //$NON-NLS-1$
		
		doSetUp();
	}
	
	protected void doSetUp()
		throws Exception {
		
		project = ResourcesPlugin.getWorkspace().getRoot().getProject(PROJECT_NAME);
		if (!project.exists()) {
			project.create(null);
		}
		
		project.open(null);
		file = project.getParent().getFile(new Path(RESOURCE_NAME));
		
		ResourceSet rset = createResourceSet();
	
		try {
			Resource originalRes = rset.getResource(
				URI.createURI(EmfTransactionTestsBundle.getEntry(
					"/test_models/test_model.extlibrary").toString()), //$NON-NLS-1$
					true);
			originalRes.setURI(URI.createPlatformResourceURI(RESOURCE_NAME));
			originalRes.save(Collections.EMPTY_MAP);
			testResource = originalRes;
			root = (Library) find("root"); //$NON-NLS-1$
		} catch (IOException e) {
			fail("Failed to load test model: " + e.getLocalizedMessage()); //$NON-NLS-1$
			
		}
		
		domain = createEditingDomain(rset);
	}

	/** May be overridden by subclasses to create non-default editing domains. */
	protected TransactionalEditingDomain createEditingDomain(ResourceSet rset) {
		return TransactionalEditingDomain.Factory.INSTANCE.createEditingDomain(rset);
	}
	
	/** May be overridden by subclasses to create non-default resource set. */
	protected ResourceSet createResourceSet() {
		return new ResourceSetImpl();
	}

    /**
     * Adds an action to perform some clean-up following completion of the test.
     * The test framework guarantees that it will at least attempt to execute
     * this action.
     * 
     * @param action the tear-down action to run
     */
    protected final void addTearDownAction(Runnable action) {
        if (tearDownActions == null) {
            tearDownActions = new java.util.ArrayList();
        }
        
        tearDownActions.add(action);
    }
    
	protected final void tearDown()
		throws Exception {
		
		try {
			doTearDown();
		} finally {
            processTearDownActions();
            trace("===> End   : " + getName()); //$NON-NLS-1$
		}
	}
    
    private void processTearDownActions() {
        if (tearDownActions != null) {
            for (Iterator iter = tearDownActions.iterator(); iter.hasNext();) {
                try {
                    Runnable action = (Runnable) iter.next();
                    action.run();
                } catch (Exception e) {
                    System.err.println("Exception in tear-down action:"); //$NON-NLS-1$
                    e.printStackTrace();
                }
            }
        }
    }

	protected void doTearDown()
		throws Exception {
		
		while (!transactionStack.isEmpty()) {
			// unwind the current transaction stack
			if (getActiveTransaction().isActive()) {
				try {
					rollback();
				} catch (Exception e) {
					// do nothing
				}
			} else {
				// just pop the closed transaction
				transactionStack.remove(transactionStack.size() - 1);
			}
		}
		
		root = null;
		if (testResource != null) {
			unloadAndRemove(testResource);
			testResource = null;
		}
		
		if ((project != null) && project.exists()) {
			project.delete(true, true, null);
		}
		
		project = null;
		domain = null;
	}

	//
	// Other framework methods
	//
	
	public static void trace(String message) {
		if (DEBUGGING) {
			System.out.println(message);
			System.out.flush();
		}
	}
	
	protected Resource createTestResource(String name) {
		Resource result = null;
		
		try {
			InputStream input =
				EmfTransactionTestsBundle.getEntry("/test_models/" + name).openStream(); //$NON-NLS-1$
			
			IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(
				new Path(PROJECT_NAME + '/' + name));
			file.create(input, true, null);
			
			result = domain.createResource(
				URI.createPlatformResourceURI(file.getFullPath().toString()).toString());
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception creating test resource: " + e.getLocalizedMessage()); //$NON-NLS-1$
		}
		
		return result;
	}
	
	protected void unloadAndRemove(Resource res) {
		if (res.isLoaded()) {
			res.unload();
		}
		
		if (res.getResourceSet() != null) {
			res.getResourceSet().getResources().remove(res);
		}
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
	
	/**
	 * Asserts that we can find an object having the specified name.
	 * 
	 * @param name the name to seek
	 * 
	 * @see #find(String)
	 */
	protected void assertFound(String name) {
		assertNotNull("Did not find " + name, find(testResource, name)); //$NON-NLS-1$
	}
	
	/**
	 * Asserts that we can find an object having the specified name, relative
	 * to the specified starting object.
	 * 
	 * @param start the object from which to start looking (to which the
	 *     <code>name</code> is relative).  This can be a resource or an
	 *     element
	 * @param name the name to seek
	 * 
	 * @see #find(Object, String)
	 */
	protected void assertFound(Object start, String name) {
		assertNotNull("Did not find " + name, find(testResource, name)); //$NON-NLS-1$
	}
	
	/**
	 * Asserts that we cannot find an object having the specified name.
	 * 
	 * @param name the name to (not) seek
	 * 
	 * @see #find(String)
	 */
	protected void assertNotFound(String name) {
		assertNull("Found " + name, find(testResource, name)); //$NON-NLS-1$
	}
	
	/**
	 * Asserts that we cannot find an object having the specified name, relative
	 * to the specified starting object.
	 * 
	 * @param start the object from which to start looking (to which the
	 *     <code>name</code> is relative).  This can be a resource or an
	 *     element
	 * @param name the name to (not) seek
	 * 
	 * @see #find(Object, String)
	 */
	protected void assertNotFound(Object start, String name) {
		assertNull("Found " + name, find(testResource, name)); //$NON-NLS-1$
	}
	
	/**
	 * Finds the object in the test model having the specified qualified name.
	 * 
	 * @param qname a slash-delimited qualified name
	 * @return the matching object, or <code>null</code> if not found
	 */
	protected EObject find(String qname) {
		return find(testResource, qname);
	}
	
	/**
	 * Finds the object in the test model having the specified qualified name,
	 * starting from some object.
	 * 
	 * @param object the starting object (resource or element)
	 * @param qname a slash-delimited qualified name, relative to the
	 *     provided <code>object</code>
	 * @return the matching object, or <code>null</code> if not found
	 */
	protected EObject find(Object start, String qname) {
		EObject result = null;
		Object current = start;
		
		String[] names = tokenize(qname);
		
		for (int i = 0; (current != null) && (i < names.length); i++) {
			String name = names[i];
			result = null;
			
			for (Iterator iter = getContents(current).iterator(); iter.hasNext();) {
				EObject child = (EObject) iter.next();
				
				if (name.equals(getName(child))) {
					result = child;
					break;
				}
			}
			
			current = result;
		}
		
		return result;
	}

	/**
	 * Gets the name of a library object.
	 * 
	 * @param object the object
	 * @return its name
	 */
	protected String getName(EObject object) {
		return (String) GetName.INSTANCE.doSwitch(object);
	}
	
	/**
	 * Gets the contents of an object.
	 * 
	 * @param object an object, which may be a resource or an element
	 * @return its immediate contents (children)
	 */
	private List getContents(Object object) {
		if (object instanceof EObject) {
			return ((EObject) object).eContents();
		} else if (object instanceof Resource) {
			return ((Resource) object).getContents();
		} else {
			return Collections.EMPTY_LIST;
		}
	}
	
	/**
	 * Tokenizes a qualified name on the slashes.
	 * 
	 * @param qname a qualified name
	 * @return the parts between the slashes
	 */
	private String[] tokenize(String qname) {
		return qname.split("/"); //$NON-NLS-1$
	}
	
	/**
	 * Switch to compute the names of library objects.
	 *
	 * @author Christian W. Damus (cdamus)
	 */
	private static final class GetName extends EXTLibrarySwitch {
		static final GetName INSTANCE = new GetName();
		
		private GetName() {
			super();
		}
		
		public Object caseAudoVisualItem(AudioVisualItem object) {
			return object.getTitle();
		}

		public Object caseBook(Book object) {
			return object.getTitle();
		}

		public Object caseLibrary(Library object) {
			return object.getName();
		}

		public Object casePeriodical(Periodical object) {
			return object.getTitle();
		}
		
		public Object caseWriter(Writer object) {
			return object.getName();
		}

		public Object casePerson(Person object) {
			if (object.getFirstName() == null) {
				if (object.getLastName() == null) {
					return ""; //$NON-NLS-1$
				} else {
					return object.getLastName();
				}
			} else if (object.getLastName() == null) {
				return object.getFirstName();
			} else {
				StringBuffer result = new StringBuffer();

				result.append(object.getFirstName()).append(' ').append(
					object.getLastName());

				return result.toString();
			}
		}

		public Object defaultCase(EObject object) {
			return ""; //$NON-NLS-1$
		}
	}
	
	/**
	 * Gets the current domain's command stack.
	 * 
	 * @return the command stack
	 */
	protected TransactionalCommandStack getCommandStack() {
		return (TransactionalCommandStack) domain.getCommandStack();
	}
	
	/**
	 * Opens a read-write transaction without options.
	 */
	protected void startWriting() {
		try {
			transactionStack.add(
					((InternalTransactionalEditingDomain) domain).startTransaction(false, null));
		} catch (Exception e) {
			fail(e);
		}
	}
	
	/**
	 * Opens a read-write transaction with one option.
	 * 
	 * @param option the option
	 */
	protected void startWriting(String option) {
		startWriting(makeOptions(option));
	}
	
	/**
	 * Opens a read-write transaction with the specified options.
	 * 
	 * @param options the options
	 */
	protected void startWriting(Map options) {
		try {
			transactionStack.add(
					((InternalTransactionalEditingDomain) domain).startTransaction(false, options));
		} catch (Exception e) {
			fail(e);
		}
	}
	
	/**
	 * Opens a read-only transaction without any options.
	 */
	protected void startReading() {
		try {
			transactionStack.add(
					((InternalTransactionalEditingDomain) domain).startTransaction(true, null));
		} catch (Exception e) {
			fail(e);
		}
	}
	
	/**
	 * Opens a read-only transaction with one option.
	 * 
	 * @param option the option
	 */
	protected void startReading(String option) {
		startReading(makeOptions(option));
	}
	
	/**
	 * Opens a read-only transaction with the specified options.
	 * 
	 * @param options the options
	 */
	protected void startReading(Map options) {
		try {
			transactionStack.add(
					((InternalTransactionalEditingDomain) domain).startTransaction(true, options));
		} catch (Exception e) {
			fail(e);
		}
	}
	
	/**
	 * Commits the most recently-opened transaction.
	 */
	protected Transaction commit() {
		Transaction result = null;
		
		try {
			result = (Transaction) transactionStack.remove(transactionStack.size() - 1);
			result.commit();
		} catch (Exception e) {
			fail(e);
		}
		
		return result;
	}
	
	/**
	 * Rolls back the most recently-opened transaction.
	 */
	protected Transaction rollback() {
		Transaction result = null;
		
		try {
			result = (Transaction) transactionStack.remove(transactionStack.size() - 1);
			result.rollback();
		} catch (Exception e) {
			fail(e);
		}
		
		return result;
	}
	
	/**
	 * Obtains the most recently-opened transaction (the "active" transaction).
	 * 
	 * @return the current transaction, or <code>null</code> if none is active
	 */
	protected InternalTransaction getActiveTransaction() {
		return transactionStack.isEmpty()
			? null
			: (InternalTransaction) transactionStack.get(transactionStack.size() - 1);
	}
	
	/**
	 * Makes a map from one option.
	 * 
	 * @param option the option to enable
	 * 
	 * @return the map
	 */
	protected Map makeOptions(String option) {
		return Collections.singletonMap(option, Boolean.TRUE);
	}
	
	/**
	 * Gets the validation statuses having the specified severity within
	 * the specified status object.
	 * 
	 * @param status a status (often a multi-status)
	 * @param severity the severity of status to look for
	 * 
	 * @return the matching statuses, or an empty collection if none found
	 */
	protected Collection findValidationStatuses(IStatus status, int severity) {
		Set result;
		
		if (status.isMultiStatus()) {
			result = new java.util.HashSet();
			IStatus[] children = status.getChildren();
			
			for (int i = 0; i < children.length; i++) {
				result.addAll(findValidationStatuses(children[i], severity));
			}
		} else if ((status instanceof IConstraintStatus)
				&& (status.getSeverity() == severity)) {
			result = Collections.singleton(status);
		} else {
			result = Collections.EMPTY_SET;
		}
		
		return result;
	}

	protected void idle(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			fail(e);
		}
	}

	protected void runGC() {
		System.gc();
		
		idle(2000);
		
		System.gc();
	}
}
