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
 * $Id: AllTests.java,v 1.3 2007/10/03 20:17:34 cdamus Exp $
 */
package org.eclipse.emf.workspace.util.tests;

import java.util.Arrays;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

import org.eclipse.core.runtime.IPlatformRunnable;

/**
 * JUnit suite for the <code>org.eclipse.emf.workspace.util</code> package.
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
		TestSuite suite = new TestSuite("Internal Utility Tests"); //$NON-NLS-1$

		suite.addTest(ResourceUndoContextTest.suite());
		suite.addTest(OperationChangeDescriptionTest.suite());
		suite.addTest(WorkspaceSynchronizerTest.suite());
        suite.addTest(ValidateEditTest.suite());
		return suite;
	}

	public Object run(Object args)
		throws Exception {

		TestRunner.run(suite());
		return Arrays
			.asList(new String[] {"Please see raw test suite output for details."}); //$NON-NLS-1$
	}
}
