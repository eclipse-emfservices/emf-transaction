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
package org.eclipse.emf.workspace.util.tests;

import java.util.Arrays;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

/**
 * JUnit suite for the <code>org.eclipse.emf.workspace.util</code> package.
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
		TestSuite suite = new TestSuite("Internal Utility Tests"); //$NON-NLS-1$

		suite.addTest(ResourceUndoContextTest.suite());
		suite.addTest(OperationChangeDescriptionTest.suite());
		suite.addTest(WorkspaceSynchronizerTest.suite());
        suite.addTest(ValidateEditTest.suite());
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
