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
 * $Id: EMFCommandOperation.java,v 1.9 2007/11/14 18:14:08 cdamus Exp $
 */
package org.eclipse.emf.workspace;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.IOperationHistory;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.command.CommandWrapper;
import org.eclipse.emf.common.command.CompoundCommand;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.edit.command.AddCommand;
import org.eclipse.emf.edit.command.MoveCommand;
import org.eclipse.emf.edit.command.RemoveCommand;
import org.eclipse.emf.edit.command.ReplaceCommand;
import org.eclipse.emf.edit.command.SetCommand;
import org.eclipse.emf.edit.domain.AdapterFactoryEditingDomain;
import org.eclipse.emf.edit.provider.IItemPropertyDescriptor;
import org.eclipse.emf.edit.provider.IItemPropertySource;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.TransactionalCommandStack;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.impl.InternalTransaction;
import org.eclipse.emf.transaction.impl.InternalTransactionalCommandStack;
import org.eclipse.emf.transaction.util.ConditionalRedoCommand;
import org.eclipse.emf.workspace.impl.EMFOperationTransaction;
import org.eclipse.emf.workspace.internal.l10n.Messages;
import org.eclipse.osgi.util.NLS;

/**
 * An operation that wraps an EMF {@link Command} to execute it in a read/write
 * transaction on an {@link IOperationHistory}.  This class may be created
 * explicitly by a client of the operation history, or it may be used implicitly
 * by executing a command on the {@link TransactionalCommandStack}.
 *
 * @author Christian W. Damus (cdamus)
 */
public class EMFCommandOperation
	extends AbstractEMFOperation {

	private final Command command;
	private Command triggerCommand;
	
	/**
	 * Initializes me with my editing domain and a command to execute.
	 * 
	 * @param domain my domain
	 * @param command my command
	 */
	public EMFCommandOperation(TransactionalEditingDomain domain, Command command) {
		this(domain, command, null);
	}
	
	/**
	 * Initializes me with my editing domain, a command to execute, and
	 * transaction options.
	 * 
	 * @param domain my domain
	 * @param command my command
	 * @param options transaction options, or <code>null</code> for the defaults
	 */
	public EMFCommandOperation(TransactionalEditingDomain domain, Command command,
			Map<?, ?> options) {
		
		super(domain, command.getLabel(), options);
		
		this.command = command;
		
		improveLabel(command);
	}
	
	/**
	 * Obtains the command that I wrap.
	 * 
	 * @return my command
	 */
	public final Command getCommand() {
		return command;
	}
	
	/**
	 * I can execute if my command can execute.
	 */
	@Override
	public boolean canExecute() {
		return super.canExecute() && command.canExecute();
	}
	
	/**
	 * Executes me by executing my command.
	 */
	@Override
	protected IStatus doExecute(IProgressMonitor monitor, IAdaptable info)
		throws ExecutionException {
		
		command.execute();
		
		return Status.OK_STATUS;
	}
	
	@Override
	protected void didCommit(Transaction transaction) {
		super.didCommit(transaction);
		
		if (!(command instanceof RecordingCommand)) {
			// the recording command already includes the triggers in its
			//    undo information
			triggerCommand = ((InternalTransaction) transaction).getTriggers();
		}
	}
	
	/**
	 * I can undo if my command or (if any) trigger command can undo.
	 */
	@Override
	public boolean canUndo() {
		return super.canUndo() &&
			(command.canUndo() && ((triggerCommand == null) || triggerCommand.canUndo()));
	}
	
	/**
	 * Undoes me by undoing my trigger command (if any) and my command.
	 */
	@Override
	protected IStatus doUndo(IProgressMonitor monitor, IAdaptable info) throws ExecutionException {
		if (triggerCommand != null) {
			triggerCommand.undo();
		}
		
		command.undo();
		
		return Status.OK_STATUS;
	}
	
	/**
	 * I can redo if either my wrapped command is a {@link ConditionalRedoCommand}
	 * that can undo, or it is not a conditionally redoable command.
	 */
	@Override
	public boolean canRedo() {
		return super.canRedo() &&
			(canRedo(command) && ((triggerCommand == null) || canRedo(triggerCommand)));
	}
	
	private boolean canRedo(Command cmd) {
		return !(cmd instanceof ConditionalRedoCommand)
				|| ((ConditionalRedoCommand) cmd).canRedo();
	}
	
	/**
	 * Redoes me by redoing my command and my trigger command (if any).
	 */
	@Override
	protected IStatus doRedo(IProgressMonitor monitor, IAdaptable info) throws ExecutionException {
		command.redo();
		
		if (triggerCommand != null) {
			triggerCommand.redo();
		}
		
		return Status.OK_STATUS;
	}
    
    /**
     * Extends the inherited implementation to additionally dispose my command
     * and my trigger command (if any).
     */
    @Override
	public void dispose() {
        super.dispose();
        
        if (command != null) {
            command.dispose();
        }
        
        if (triggerCommand != null) {
            triggerCommand.dispose();
        }
    }

	/**
	 * Creates a different kind of transaction that knows about this operation.
	 * 
	 * @see EMFOperationTransaction
	 */
	@Override
	Transaction createTransaction(Map<?, ?> options) throws InterruptedException {
		InternalTransactionalCommandStack stack =
			(InternalTransactionalCommandStack) getEditingDomain().getCommandStack();
		
		EMFOperationTransaction result =
			(EMFOperationTransaction) stack.createTransaction(command, options);
		
		result.setOperation(this);
		
		return result;
	}
	
	/**
	 * Computes a more user-friendly label for the operation than the label
	 * created by default for EMF's feature-changing commands.
	 * 
	 * @param cmd a command
	 */
	protected void improveLabel(Command cmd) {
		EStructuralFeature feature = null;
		EObject owner = null;
		String pattern = null;
		
		// unwrap any wrappers
		while (cmd instanceof CommandWrapper) {
			cmd = ((CommandWrapper) cmd).getCommand();
		}
		
		if (cmd instanceof CompoundCommand) {
			CompoundCommand compound = (CompoundCommand) cmd;
			List<Command> nested = compound.getCommandList();
			if (!nested.isEmpty()) {
				improveLabel(nested.get(0));
				return;
			}
		} else if (cmd instanceof SetCommand) {
			feature = ((SetCommand) cmd).getFeature();
			owner = ((SetCommand) cmd).getOwner();
			
			pattern = Messages.setLabel; 
		} else if (cmd instanceof AddCommand) {
			feature = ((AddCommand) cmd).getFeature();
			owner = ((AddCommand) cmd).getOwner();
			
			pattern = Messages.addLabel; 
		} else if (cmd instanceof RemoveCommand) {
			feature = ((RemoveCommand) cmd).getFeature();
			owner = ((RemoveCommand) cmd).getOwner();
			
			pattern = Messages.removeLabel; 
		} else if (cmd instanceof MoveCommand) {
			feature = ((MoveCommand) cmd).getFeature();
			owner = ((MoveCommand) cmd).getOwner();
			
			pattern = Messages.moveLabel; 
		} else if (cmd instanceof ReplaceCommand) {
			feature = ((ReplaceCommand) cmd).getFeature();
			owner = ((ReplaceCommand) cmd).getOwner();
			
			pattern = Messages.replaceLabel; 
		}
		
		if (feature != null) {
			IItemPropertySource source = (IItemPropertySource)
				((AdapterFactoryEditingDomain) getEditingDomain()).getAdapterFactory().adapt(
						owner, IItemPropertySource.class);
			
			if (source != null) {
				Collection<IItemPropertyDescriptor> descriptors =
					source.getPropertyDescriptors(owner);
				
				for (IItemPropertyDescriptor next : descriptors) {
					if (next.getFeature(owner) == feature) {
						setLabel(NLS.bind(pattern, next.getDisplayName(owner)));
						break;
					}
				}
			}
		}
	}
}
