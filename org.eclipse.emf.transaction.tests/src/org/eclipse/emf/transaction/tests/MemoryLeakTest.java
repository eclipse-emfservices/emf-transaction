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
 * $Id: MemoryLeakTest.java,v 1.2 2006/10/10 14:31:40 cdamus Exp $
 */
package org.eclipse.emf.transaction.tests;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.util.CompositeChangeDescription;

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
	
	/**
	 * Tests that a child transaction does not cause any interruption in the
	 * recording of the parent transaction's change description if the child is
	 * unrecorded.  The result is that we do not retain empty change
	 * descriptions and the parent has a single, seamless change description.
	 */
	public void test_nonRecordingChildTransactions_153908() {
		// report initial heap size
		long initialUsedHeap = usedHeap();
		
		startWriting();
		
		final String oldName = root.getName();
		 
		// make a change in the root-level transaction
		root.setName("foo"); //$NON-NLS-1$
		
		// now, create 1000 nested transactions, each doing a change, *but* unrecorded
		Map options = Collections.singletonMap(Transaction.OPTION_UNPROTECTED, Boolean.TRUE);
		for (int i = 0; i < 1000; i++) {
			startWriting(options);
			
			root.setName("foo" + i); //$NON-NLS-1$
			
			commit();
			
			// make another change in the root-level transaction
			root.setName("foo" + (i + 1000)); //$NON-NLS-1$
		}
		
		// report the used heap
		long currentUsedHeap = usedHeap();
		System.out.println("Additional heap used by the transaction: " //$NON-NLS-1$
				+ ((currentUsedHeap - initialUsedHeap) / 1024L) + " kB"); //$NON-NLS-1$
		
		Transaction tx = commit();
		CompositeChangeDescription change = (CompositeChangeDescription) tx.getChangeDescription();
		List children = getChildren(change);
		
		assertEquals(1, children.size());
		
		// let's just make sure that undo works as expected
		startWriting(options);
		
		final String newName = root.getName();
		
		change.applyAndReverse();
		commit();
		assertEquals(oldName, root.getName());
		
		// and redo, too
		startWriting(options);
		change.applyAndReverse();
		commit();
		assertEquals(newName, root.getName());
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
	
	protected long usedHeap() {
		Runtime rt = Runtime.getRuntime();
		
		rt.gc();
		idle(2000);
		rt.gc();
		
		long result = rt.totalMemory() - rt.freeMemory();
		
		System.out.println("Used Heap: " + (result / 1024L) + " kB"); //$NON-NLS-1$ //$NON-NLS-2$
		
		return result;
	}
	
	static List getChildren(CompositeChangeDescription compositeChange) {
		List result = null;
		
		try {
			Field children = compositeChange.getClass().getDeclaredField("changes"); //$NON-NLS-1$
			children.setAccessible(true);
			
			result = (List) children.get(compositeChange);
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getLocalizedMessage());
		}
		
		return result;
	}
}
