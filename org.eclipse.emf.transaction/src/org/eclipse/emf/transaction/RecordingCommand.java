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
 * $Id: RecordingCommand.java,v 1.3 2006/03/15 01:40:31 cdamus Exp $
 */
package org.eclipse.emf.transaction;

import org.eclipse.emf.common.command.AbstractCommand;
import org.eclipse.emf.transaction.impl.InternalTransactionalEditingDomain;

/**
 * A partial {@link org.eclipse.emf.common.command.Command} implementation that
 * records the changes made by a subclass's direct manipulation of objects via
 * the metamodel's API.  This simplifies the programming model for complex
 * commands (not requiring composition of set/add/remove commands) while
 * still providing automatic undo/redo support.
 * <p>
 * Subclasses are simply required to implement the {@link #doExecute()} method
 * to make the desired changes to the model.  Note that, because changes are
 * recorded for automatic undo/redo, the concrete command must not make any
 * changes that cannot be recorded by EMF (unless it does not matter that they
 * will not be undone).
 * </p>
 *
 * @author Christian W. Damus (cdamus)
 */
public abstract class RecordingCommand extends AbstractCommand {
	private final TransactionalEditingDomain domain;
	private Transaction transaction;
	private TransactionChangeDescription change;
	
	/**
	 * Initializes me with the editing domain in which I am to be executed.
	 * 
	 * @param domain my domain
	 */
	public RecordingCommand(TransactionalEditingDomain domain) {
		super();
		
		this.domain = domain;
	}

	/**
	 * Initializes me with my editing domain and a human-readable label.
	 * 
	 * @param domain my domain
	 * @param label my user-friendly label
	 */
	public RecordingCommand(TransactionalEditingDomain domain, String label) {
		super(label);
		
		this.domain = domain;
	}

	/**
	 * Initializes me with my editing domain, label, and more expansive
	 * description.
	 * 
	 * @param domain my domain
	 * @param label my label
	 * @param description my long description
	 */
	public RecordingCommand(TransactionalEditingDomain domain, String label, String description) {
		super(label, description);
		
		this.domain = domain;
	}
	
	/**
	 * Subclasses should override this if they have more preparation to do.
	 * By default, the result is just <code>true</code>.
	 */
	protected boolean prepare() {
		return true;
	}
	
	/**
	 * Implements the execution with automatic recording of undo information.
	 * Delegates the actual model changes to the subclass's implementation of
	 * the {@link #doExecute()} method.
	 * 
	 * @see #doExecute()
	 */
	public final void execute() {
		// invoke the subclass before getting the transaction, because if an
		//    exception occurs, we don't want to be undoable
		doExecute();
		
		transaction = ((InternalTransactionalEditingDomain) domain).getActiveTransaction();
	}

	/**
	 * I can be undone if I successfully recorded the changes that I executed.
	 * Subclasses would not normally need to override this method.
	 */
	public boolean canUndo() {
		if ((change == null) && (transaction != null)) {
			change = transaction.getChangeDescription();
		}
		
		return (change == null) || change.canApply();
	}
	
	/**
	 * Undoes the changes that I recorded.
	 * Subclasses would not normally need to override this method.
	 */
	public final void undo() {
		if (change != null) {
			change.applyAndReverse();
		}
	}
	
	/**
	 * Redoes the changes that I recorded.
	 * Subclasses would not normally need to override this method.
	 */
	public final void redo() {
		if (change != null) {
			change.applyAndReverse();
		}
	}
	
	/**
	 * Implemented by subclasses to perform the necessary changes in the model.
	 * These changes are applied by direct manipulation of the model objects,
	 * <em>not</em> by executing commands.
	 */
	protected abstract void doExecute();
}
