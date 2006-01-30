/**
 * <copyright>
 *
 * Copyright (c) 2005, 2006 IBM Corporation and others.
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
 * $Id: TestOperation.java,v 1.2 2006/01/30 19:47:57 cdamus Exp $
 */
package org.eclipse.emf.workspace.tests.fixtures;

import java.util.Map;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.workspace.AbstractEMFOperation;

/**
 * A simple abstract operation for testing purposes.
 *
 * @author Christian W. Damus (cdamus)
 */
public abstract class TestOperation
	extends AbstractEMFOperation {

	public TestOperation(TransactionalEditingDomain domain) {
		super(domain, "Testing"); //$NON-NLS-1$
	}

	public TestOperation(TransactionalEditingDomain domain, Map options) {
		super(domain, "Testing", options); //$NON-NLS-1$
	}

	protected final IStatus doExecute(IProgressMonitor monitor, IAdaptable info) throws ExecutionException {
		doExecute();
		
		return Status.OK_STATUS;
	}
	
	protected abstract void doExecute();
}
