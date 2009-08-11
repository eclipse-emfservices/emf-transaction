/******************************************************************************
 * Copyright (c) 2009 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation 
 ****************************************************************************/
package org.eclipse.emf.transaction.util;

import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.emf.transaction.internal.ITransactionLock;

/**
 * A lock which does not provide any mutual exclusion. This implementation is
 * needed for rudimentary standalone scenario support.
 * 
 * @author Boris Gruschko
 * @since 1.4
 * 
 * @noinstantiate This class is not intended to be instantiated by clients.
 */
public final class EmptyLock implements ITransactionLock {

	private AtomicInteger count;

	public EmptyLock() {
		count = new AtomicInteger(0);
	}

	public void acquire(boolean exclusive) throws InterruptedException {
		acquire(0, exclusive);
	}

	public boolean acquire(long timeout, boolean exclusive)
			throws InterruptedException {
		count.incrementAndGet();

		return true;
	}

	public void checkedTransfer(Thread thread) {
	}

	public int getDepth() {
		return count.get();
	}

	public Thread getOwner() {
		return Thread.currentThread();
	}

	public void release() {
		count.decrementAndGet();
	}

	public void uiSafeAcquire(boolean exclusive) throws InterruptedException {
		acquire(exclusive);
	}

	public boolean yield() {
		return true;
	}

}
