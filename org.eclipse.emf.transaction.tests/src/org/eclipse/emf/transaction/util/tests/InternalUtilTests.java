/**
 * <copyright>
 *
 * Copyright (c) 2005 IBM Corporation and others.
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
 * $Id: InternalUtilTests.java,v 1.1 2006/01/03 20:51:13 cdamus Exp $
 */
package org.eclipse.emf.transaction.util.tests;

import java.util.Arrays;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

import org.eclipse.core.runtime.IPlatformRunnable;

/**
 * Suite of tests for the utility classes in the EMF-TX API implementation.
 * 
 * @author Christian W. Damus (cdamus)
 */
public class InternalUtilTests
	extends TestCase
	implements IPlatformRunnable {

	public InternalUtilTests() {
		super(""); //$NON-NLS-1$
	}

	public static Test suite() {
		TestSuite suite = new TestSuite("Internal Utility Tests"); //$NON-NLS-1$

		suite.addTest(LockTest.suite());
		suite.addTest(CompositeChangeDescriptionTest.suite());

		return suite;
	}

	public Object run(Object args)
		throws Exception {

		TestRunner.run(suite());
		return Arrays
			.asList(new String[] {"Please see raw test suite output for details."}); //$NON-NLS-1$
	}
}