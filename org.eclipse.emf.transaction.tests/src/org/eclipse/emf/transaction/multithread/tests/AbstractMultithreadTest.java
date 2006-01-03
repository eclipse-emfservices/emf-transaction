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
 * $Id: AbstractMultithreadTest.java,v 1.1 2006/01/03 20:51:13 cdamus Exp $
 */
package org.eclipse.emf.transaction.multithread.tests;

import java.util.Arrays;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

import org.eclipse.core.runtime.IPlatformRunnable;
import org.eclipse.emf.transaction.TXEditingDomain;
import org.eclipse.emf.transaction.tests.AbstractTest;

/**
 * Abstract JUnit test suite for the <em>EMF-TX API</em> multi-threading tests.
 * 
 * @author Christian W. Damus (cdamus)
 */
public class AbstractMultithreadTest
	extends TestCase
	implements IPlatformRunnable {

	private TXEditingDomain domain = null;
	
	public AbstractMultithreadTest() {
		super(""); //$NON-NLS-1$
	}

	public static Test suite() {
		TestSuite suite = new TestSuite("Multi-threading Tests"); //$NON-NLS-1$

		suite.addTest(ReadOperationTest.suite());
		suite.addTest(WriteOperationTest.suite());
		suite.addTest(ReadWriteOperationTest.suite());

		return suite;
	}

	public Object run(Object args)
		throws Exception {

		TestRunner.run(suite());
		return Arrays
			.asList(new String[] {"Please see raw test suite output for details."}); //$NON-NLS-1$
	}
	
	//
	// Fixture methods
	//
	
	protected TXEditingDomain getDomain() {
		return domain;
	}
	
	protected void setUp()
		throws Exception {
		
		AbstractTest.trace("===> Begin : " + getName()); //$NON-NLS-1$
		
		super.setUp();
		
		domain = TXEditingDomain.Factory.INSTANCE.createEditingDomain();
	}
	
	protected void tearDown()
		throws Exception {
		
		domain = null;
		
		AbstractTest.trace("===> End   : " + getName()); //$NON-NLS-1$
		
		super.tearDown();
	}
}