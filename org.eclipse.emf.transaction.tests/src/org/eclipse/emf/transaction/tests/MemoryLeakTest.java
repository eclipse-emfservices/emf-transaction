/**
 * <copyright>
 *
 * Copyright (c) 2006 IBM Corporation and others.
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
 * $Id: MemoryLeakTest.java,v 1.1 2006/01/26 20:44:48 cdamus Exp $
 */
package org.eclipse.emf.transaction.tests;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

import junit.framework.Test;
import junit.framework.TestSuite;


/**
 * Tests to check for memory leaks.
 *
 * @author Christian W. Damus (cdamus)
 */
public class MemoryLeakTest extends AbstractTest {
	public MemoryLeakTest(String name) {
		super(name);
	}

	public static Test suite() {
		return new TestSuite(MemoryLeakTest.class, "Memory Leak (GC) Tests"); //$NON-NLS-1$
	}

	/**
	 * Tests that unloading a resource allows its root(s) to be reclaimed.
	 */
	public void test_unloadResource() {
		ReferenceQueue q = new ReferenceQueue();
		Reference ref = new WeakReference(root, q);
		
		// make some change, causing a transaction to record a change description
		startWriting();
		root.setName("foo"); //$NON-NLS-1$
		commit();
		
		startReading();
		testResource.unload();
		commit();
		
		root = null; // forget our only other reference to this object

		System.gc();
		
		idle(2000);
		
		System.gc();
		
		assertSame(ref, q.poll());
	}

	/**
	 * Tests that an editing domain can be reclaimed if we forget all references
	 * to it and its resource set's contents.
	 */
	public void test_reclaimEditingDomain() {
		ReferenceQueue q = new ReferenceQueue();
		Reference ref = new WeakReference(domain, q);
		
		// make some change, causing a transaction to record a change description
		startWriting();
		root.setName("foo"); //$NON-NLS-1$
		commit();
		
		domain = null;        // forget the domain
		testResource = null;  // forget a resource in the domain
		root = null;          // forget an object in the domain
		
		System.gc();
		
		idle(2000);
		
		System.gc();
		
		assertSame(ref, q.poll());
	}
	
	//
	// Fixture methods
	//
	
	protected void idle(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			fail(e);
		}
	}
}
