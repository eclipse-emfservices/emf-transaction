/**
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   IBM - Initial API and implementation
 */
package org.eclipse.emf.workspace.tests;

import java.util.Arrays;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

/**
 * Master JUnit test suite for the <em>EMF Workbench API</em>.
 * 
 * @author Christian W. Damus (cdamus)
 */
public class AllTests
	extends TestCase
	implements IApplication {

	public AllTests() {
		super(""); //$NON-NLS-1$
	}

	public static Test suite() {
		TestSuite suite = new TestSuite("EMF Workbench JUnit Tests"); //$NON-NLS-1$

		suite.addTest(BasicWorkbenchTest.suite());
		suite.addTest(AbstractEMFOperationTest.suite());
		suite.addTest(EMFCommandOperationTest.suite());
		suite.addTest(CompositeEMFOperationTest.suite());
		suite.addTest(EMFOperationCommandTest.suite());
		suite.addTest(org.eclipse.emf.workspace.util.tests.AllTests.suite());
		suite.addTest(UndoContextTest.suite());
		suite.addTest(WorkbenchCommandStackTest.suite());
        suite.addTest(MemoryLeakTest.suite());

		return suite;
	}

	public Object start(IApplicationContext context)
		throws Exception {

		TestRunner.run(suite());
		return Arrays
			.asList(new String[] {"Please see raw test suite output for details."}); //$NON-NLS-1$
	}
	
	public void stop() {
		// nothing to do
	}
}
