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
 * $Id: AllTests.java,v 1.2 2007/06/07 14:26:03 cdamus Exp $
 */
package org.eclipse.emf.workspace.tests;

import java.util.Arrays;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

import org.eclipse.core.runtime.IPlatformRunnable;

/**
 * Master JUnit test suite for the <em>EMF Workbench API</em>.
 * 
 * @author Christian W. Damus (cdamus)
 */
public class AllTests
	extends TestCase
	implements IPlatformRunnable {

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

		return suite;
	}

	public Object run(Object args)
		throws Exception {

		TestRunner.run(suite());
		return Arrays
			.asList(new String[] {"Please see raw test suite output for details."}); //$NON-NLS-1$
	}
}
