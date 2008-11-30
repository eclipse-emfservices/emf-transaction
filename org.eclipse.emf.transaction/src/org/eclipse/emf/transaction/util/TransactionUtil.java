/**
 * <copyright>
 *
 * Copyright (c) 2006, 2008 IBM Corporation, Zeligsoft Inc., and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   IBM - Initial API and implementation
 *   Zeligsoft - Bug 245446
 *
 * </copyright>
 *
 * $Id: TransactionUtil.java,v 1.8 2008/11/30 16:38:08 cdamus Exp $
 */
package org.eclipse.emf.transaction.util;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.change.ChangeDescription;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.edit.domain.EditingDomain;
import org.eclipse.emf.edit.domain.IEditingDomainProvider;
import org.eclipse.emf.transaction.RunnableWithResult;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.impl.InternalTransactionalEditingDomain;
import org.eclipse.emf.transaction.impl.TransactionChangeRecorder;

/**
 * Static utilities for dealing with EMF elements and resources in a
 * transactional editing domain.
 *
 * @author Christian W. Damus (cdamus)
 */
public class TransactionUtil {
	/** Cannot instantiate. */
	private TransactionUtil() {
		super();
	}
	
	/**
	 * Obtains the transactional editing domain that manages the specified
	 * element.
	 * 
	 * @param eObject an EMF model element
	 * 
	 * @return its editing domain, or <code>null</code> if it is not attached
	 *     to any resource in an editing domain
	 */
	public static TransactionalEditingDomain getEditingDomain(EObject eObject) {
		TransactionalEditingDomain result = null;
		Resource res = eObject.eResource();
		
		if (res != null) {
			result = getEditingDomain(res);
		}
		
		return result;
	}
	
	/**
	 * Obtains the transactional editing domain that manages the specified
	 * resource.
	 * 
	 * @param resource a resource
	 * 
	 * @return its editing domain, or <code>null</code> if it is not in a
	 *     resource set managed by an editing domain
	 */
	public static TransactionalEditingDomain getEditingDomain(Resource resource) {
		TransactionalEditingDomain result = null;
		ResourceSet rset = resource.getResourceSet();
		
		if (rset != null) {
			result = getEditingDomain(rset);
		}
		
		return result;
	}
	
	/**
	 * Obtains the transactional editing domain that manages the specified
	 * resource set.
	 * 
	 * @param rset a resource set
	 * 
	 * @return its editing domain, or <code>null</code> if it is managed by
	 *     an editing domain
	 */
	public static TransactionalEditingDomain getEditingDomain(ResourceSet rset) {
		return TransactionalEditingDomain.Factory.INSTANCE.getEditingDomain(rset);
	}
	
	/**
	 * Obtains the transactional editing domain that manages the specified
	 * object.  This is useful, for example, to get the editing domain from
	 * which a {@link org.eclipse.emf.common.notify.Notification} originated by
	 * testing its
	 * {@link org.eclipse.emf.common.notify.Notification#getNotifier() notifier}.
	 * 
	 * @param object some object
	 * 
	 * @return its editing domain, or <code>null</code> if it is not of a type
	 *     that is managed by an editing domain or if it is of an appropriate
	 *     type but happens not to be managed by an editing domain
	 */
	public static TransactionalEditingDomain getEditingDomain(Object object) {
		TransactionalEditingDomain result = null;
		
		if (object instanceof EObject) {
			result = getEditingDomain((EObject) object);
		} else if (object instanceof Resource) {
			result = getEditingDomain((Resource) object);
		} else if (object instanceof ResourceSet) {
			result = getEditingDomain((ResourceSet) object);
		} else if (object instanceof Transaction) {
			result = ((Transaction) object).getEditingDomain();
		} else if (object instanceof TransactionalEditingDomain) {
			result = (TransactionalEditingDomain) object;
		} else if (object instanceof IEditingDomainProvider) {
			EditingDomain domain = ((IEditingDomainProvider) object).getEditingDomain();
			
			if (domain instanceof TransactionalEditingDomain) {
				result = (TransactionalEditingDomain) domain;
			}
		}
		
		return result;
	}
	
	/**
	 * <p>
	 * Disconnects the specified resource from its editing domain, so that it is
	 * released from the constraints of the transactional environment.  Note
	 * that this is only permitted if the resource is not currently attached to
	 * the resource set of the editing domain in question.
	 * </p><p>
	 * This should only be done with extreme caution.  If any other resources
	 * that are still being managed by the transactional editing domain have
	 * dependencies on this <tt>resource</tt>, then existing undo/redo
	 * information for these may be corrupted and future undo recording may not
	 * be complete.  It is <b>highly recommended</b> to flush the command stack
	 * of the editing domain in question after disconnecting a resource from it.
	 * </p>
	 * 
	 * @param resource a resource to disconnect from its current editing domain,
	 *     if any
	 * 
	 * @throws IllegalStateException if the specified <tt>resource</tt> is
	 *     still in the {@link ResourceSet} managed by its current editing
	 *     domain
	 * 
	 * @since 1.1
	 */
	public static void disconnectFromEditingDomain(Resource resource) {
		disconnectFromEditingDomain0(resource);
	}
	
	/**
	 * <p>
	 * Disconnects the specified element from its editing domain, so that it is
	 * released from the constraints of the transactional environment.  Note
	 * that this is only permitted if the element is not currently attached to
	 * the resource set of the editing domain in question.
	 * </p><p>
	 * This should only be done with extreme caution.  If any other elements
	 * that are still being managed by the transactional editing domain have
	 * dependencies on this <tt>eobject</tt>, then existing undo/redo
	 * information for these may be corrupted and future undo recording may not
	 * be complete.  It is <b>highly recommended</b> to flush the command stack
	 * of the editing domain in question after disconnecting an element from it.
	 * </p><p>
	 * It is probably more useful to
	 * {@linkplain #disconnectFromEditingDomain(Resource) disconnect} an entire
	 * {@link Resource} from the editing domain instead of just an object,
	 * unless that object is being moved from one editing domain to another.
	 * </p>
	 * 
	 * @param eobject a model element to disconnect from its current editing
	 *     domain, if any
	 * 
	 * @throws IllegalStateException if the specified <tt>eobject</tt> is
	 *     still in the {@link ResourceSet} managed by its current editing
	 *     domain
	 * 
	 * @since 1.1
	 * 
	 * @see #disconnectFromEditingDomain(Resource)i
	 */
	public static void disconnectFromEditingDomain(EObject eobject) {
		disconnectFromEditingDomain0(eobject);
	}
	
	/**
	 * Implements the disconnection of any notifier from the transactional
	 * editing domain.
	 * 
	 * @param notifier the notifier to disconnect
	 */
	private static void disconnectFromEditingDomain0(Notifier notifier) {
		Set<TransactionChangeRecorder> recorders = getExistingChangeRecorders(notifier);
		
		if (!recorders.isEmpty()) {
			// this resource is managed by a transactional editing domain
			InternalTransactionalEditingDomain domain =
				(InternalTransactionalEditingDomain) getEditingDomain(notifier);
			
			if ((domain != null) && recorders.contains(domain.getChangeRecorder())) {
				throw new IllegalArgumentException("resource is still in the domain's resource set"); //$NON-NLS-1$
			}
			
			Iterator<?> iter = EcoreUtil.getAllProperContents(Collections.singleton(
					notifier), false);
			while (iter.hasNext()) {
				((Notifier) iter.next()).eAdapters().removeAll(recorders);
			}
		}
	}
	
	/**
	 * Obtains the transaction change recorders currently attached to a notifier.
	 * 
	 * @param notifier a notifier
	 * @return the currently attached change recorders, which may be an empty
	 *    set if none
	 */
	private static Set<TransactionChangeRecorder> getExistingChangeRecorders(
			Notifier notifier) {
		
		Set<TransactionChangeRecorder> result = null;
		
		Object[] adapters = notifier.eAdapters().toArray();
		for (Object element : adapters) {
			if (element instanceof TransactionChangeRecorder) {
				TransactionChangeRecorder next = (TransactionChangeRecorder) element;
				
				if (next.getEditingDomain() != null) {
					if (result == null) {
						result = new java.util.HashSet<TransactionChangeRecorder>();
					}
					
					result.add(next);
				}
			}
		}
		
		return (result == null)? Collections.<TransactionChangeRecorder>emptySet()
			: result;
	}
    
    /**
     * Disposes a change description.  Currently, this just clears the adapters
     * list of all objects in the change description.
     * 
     * @param change a change description to dispose
     * 
     * @since 1.1
     */
    public static void dispose(ChangeDescription change) {
        if (change instanceof CompositeChangeDescription) {
            ((CompositeChangeDescription) change).dispose();
        } else if (change instanceof CommandChangeDescription) {
            ((CommandChangeDescription) change).dispose();
        } else {
            for (Iterator<EObject> iter = change.eAllContents(); iter.hasNext();) {
                iter.next().eAdapters().clear();
            }
        }
    }
    
    /**
     * Obtains an instance of the specified adapter type for an editing domain.
     * 
     * @param <T> the adapter interface that is required
     * 
     * @param domain an editing domain to adapt
     * @param adapterType the required interface
     * 
     * @return an instance of the required interface that adapts the
     *    <tt>domain</tt>, or <code>null</code> if it does not supply this interface
     * 
     * @since 1.2
     */
    public static <T> T getAdapter(TransactionalEditingDomain domain,
    		Class<? extends T> adapterType) {
        T result;
        
        if (domain instanceof Adaptable) {
            result = ((Adaptable) domain).getAdapter(adapterType);
        } else {
            result = null;
        }
        
        return result;
    }
    
    /**
     * Utility method for executing exclusive runnables that
     * {@linkplain RunnableWithResult return values}.  The advantage of this
     * method over {@link TransactionalEditingDomain#runExclusive(Runnable)} is
     * that it provides compile-time type safety.
     * 
     * @param <T> the result type of the runnable
     * 
     * @param domain the editing domain in which to run
     * @param runnable the runnable to execute
     * 
     * @return the result of the runnable
     * 
     * @throws InterruptedException if the current thread is interrupted while
	 *    waiting for access to the resource set
     * 
     * @since 1.2
     */
    @SuppressWarnings("unchecked")
	public static <T> T runExclusive(TransactionalEditingDomain domain,
    		RunnableWithResult<? extends T> runnable) throws InterruptedException {
    	
    	return (T) domain.runExclusive(runnable);
    }
    
    /**
     * Utility method for providing privileged access to runnables that
     * {@linkplain RunnableWithResult return values}.  The advantage of this
     * method over {@link TransactionalEditingDomain#createPrivilegedRunnable(Runnable)} is
     * that it provides compile-time type safety.
     * 
     * @param <T> the result type of the runnable
     * 
     * @param domain the editing domain in which to grant privileged access
     * @param runnable the runnable to execute
     * 
     * @return the result of the runnable
     * 
     * @throws InterruptedException if the current thread is interrupted while
	 *    waiting for access to the resource set
     * 
     * @since 1.2
     */
    @SuppressWarnings("unchecked")
	public static <T> RunnableWithResult<T> createPrivilegedRunnable(
			TransactionalEditingDomain domain,
    		RunnableWithResult<? extends T> runnable) throws InterruptedException {
    	
    	return (RunnableWithResult<T>) domain.createPrivilegedRunnable(runnable);
    }
    
	/**
	 * Gets the best transaction-option registry available for the specified
	 * editing <tt>domain</tt>. Usually, it will be a registry that is local to
	 * that domain, but it may be the
	 * {@linkplain Transaction.OptionMetadata.Registry#INSTANCE shared instance}
	 * under extreme circumstances.
	 * 
	 * @param domain
	 *            an editing domain
	 * @return its transaction options registry, never <code>null</code>
	 * 
	 * @since 1.3
	 */
	public static Transaction.OptionMetadata.Registry getTransactionOptionRegistry(
			TransactionalEditingDomain domain) {
		Transaction.OptionMetadata.Registry result = getAdapter(domain,
			Transaction.OptionMetadata.Registry.class);

		if (result == null) {
			result = Transaction.OptionMetadata.Registry.INSTANCE;
		}

		return result;
	}
}
