/**
 * Copyright (c) 2007 IBM Corporation and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   IBM - Initial API and implementation
 */
package org.eclipse.emf.transaction.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.edit.domain.EditingDomain;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.impl.TransactionChangeRecorder;
import org.eclipse.emf.transaction.internal.EMFTransactionPlugin;
import org.eclipse.emf.transaction.internal.EMFTransactionStatusCodes;
import org.eclipse.emf.transaction.internal.l10n.Messages;
import org.eclipse.osgi.util.NLS;


/**
 * <p>
 * Interface for the {@link Transaction#OPTION_VALIDATE_EDIT} transaction option
 * to check, when a transaction commits, that all of the resources that it has
 * modified are actually editable or (by some means supplied by the platform)
 * can be made to be editable.  The root-level transaction should assign this
 * to the {@link TransactionChangeRecorder} when it starts and remove it when
 * it closes.
 * </p><p>
 * Clients may implement this interface or extend the
 * {@linkplain ValidateEditSupport.Default default implementation}.
 * </p>
 * 
 * @see Transaction#OPTION_VALIDATE_EDIT
 * @see Transaction#OPTION_VALIDATE_EDIT_CONTEXT
 * @see ValidateEditSupport.Default
 * 
 * @author Christian W. Damus (cdamus)
 * 
 * @since 1.2
 */
public interface ValidateEditSupport {
    /**
     * Performs validate-edit for a transaction.
     * 
     * @param transaction the root-level transaction that is attempting to commit
     * @param context usually a <code>org.eclipse.swt.widgets.Shell</code>
     *    providing a UI context for interaction with the user to make resources
     *    modifiable, or <code>null</code> if no such context is available and
     *    the system should attempt to automatically validate
     * 
     * @return a status indicating <tt>OK</tt> if validate-edit succeeded,
     *    otherwise some reason why it failed and the transaction should roll
     *    back
     */
    IStatus validateEdit(Transaction transaction, Object context);
    
    /**
     * Processes a <tt>notification</tt> from a <tt>resource</tt> that may
     * either indicate that the resource should be added to the validate
     * list (i.e., its persisted state is changed) or it should be removed
     * from the validate list (e.g., because it has been unloaded).
     * 
     * @param resource a resource to add or remove to/from the validate list
     * @param notification a notification
     */
    void handleResourceChange(Resource resource, Notification notification);
    
    /**
     * Notifies me that rollback has occurred and I should clean up.  This may
     * involving touching, in some way, the resources that I validated.
     */
    void finalizeForRollback();
    
    /**
     * Notifies me that the transaction has successfully committed and I should
     * clean up.  This may involve touching, in some way, the resources that I
     * validated.
     */
    void finalizeForCommit();
    
    /**
     * <p>
     * A default implementation of the {@link ValidateEditSupport} interface,
     * that uses the editing domain's read-only resource map to determine
     * whether a resource can be edited and depends on notifications of the
     * {@link Resource#isModified()} property changing to track which resources
     * need to be validated (note that this implies that the resource is
     * tracking modifications).  It does not do any user prompting.
     * </p><p>
     * In anticipation of rollback, this implementation resets any resources
     * that were modified back to unmodified state (as it only validates those
     * that were previously unmodified).
     * </p>
     * 
     * @author Christian W. Damus (cdamus)
     * 
     * @since 1.2
     */
    class Default implements ValidateEditSupport {
        private final Set<Resource> resourcesToValidate = new java.util.HashSet<Resource>();
        
        /**
         * Initializes me.
         */
        public Default() {
            super();
        }

        /**
         * Obtains the set of resources that we have determined need to be
         * validated for edit, because they have changed from being unmodified
         * to modified.
         * 
         * @return the resources to validate-edit
         */
        protected final Set<Resource> getResourcesToValidate() {
            return resourcesToValidate;
        }
        
        public IStatus validateEdit(Transaction transaction, Object context) {
            IStatus result;
            
            if (resourcesToValidate.isEmpty()) {
                result = Status.OK_STATUS;
            } else {
                result = doValidateEdit(transaction, resourcesToValidate, context);
            }
            
            return result;
        }

        /**
         * Performs the actual edit validation.  May be overridden by subclasses
         * to provide a different mechanism.
         * 
         * @param transaction the transaction that is attempting to commit
         * @param resources the resources to validate-edit
         * @param context the validate-edit context, or <code>null</code> if none
         * 
         * @return the result of the validate-edit attempt
         */
        protected IStatus doValidateEdit(Transaction transaction,
                Collection<? extends Resource> resources, Object context) {
            IStatus result = Status.OK_STATUS;

            EditingDomain domain = transaction.getEditingDomain();

            for (Iterator<? extends Resource> iter = resources.iterator(); result.isOK()
                    && iter.hasNext();) {
                
                Resource next = iter.next();

                if (domain.isReadOnly(next)) {
                    result = new Status(IStatus.ERROR, EMFTransactionPlugin
                        .getPluginId(),
                        EMFTransactionStatusCodes.VALIDATION_FAILURE, NLS.bind(
                            Messages.modifyReadOnlyResource, next.getURI()
                                .toString()), null);
                }
            }

            return result;
        }
        
        /**
         * Rolls back the modified state of the resources that were validated to
         * let them once again be un-modified, then forgets them.
         */
        public void finalizeForRollback() {
            if (!resourcesToValidate.isEmpty()) {
                // protect against concurrent modifications: setting modified
                //   state to false will trigger removal from the collection
                Resource[] resources = resourcesToValidate.toArray(
                	new Resource[resourcesToValidate.size()]);
                
                for (Resource element : resources) {
                    // note that this is exempt from the transaction protocol
                    element.setModified(false);
                    
                    // setting modified to false should have removed them 
                    // already, but just in case ...
                    resourcesToValidate.clear();
                }
            }
        }
        
        /**
         * The default implementation simply forgets the resources to validate.
         */
        public void finalizeForCommit() {
            resourcesToValidate.clear();
        }
        
        public void handleResourceChange(Resource resource,
                Notification notification) {
            
            if (notification.getNotifier() == resource) {
                int featureID = notification.getFeatureID(Resource.class);
                
                if (featureID == Resource.RESOURCE__IS_MODIFIED) {
                    if (Boolean.TRUE.equals(notification.getNewValue())) {
                        resourcesToValidate.add(resource);
                    } else {
                        resourcesToValidate.remove(resource);
                    }
                } else if (featureID == Resource.RESOURCE__IS_LOADED) {
                    if (Boolean.FALSE.equals(notification.getNewValue())) {
                        resourcesToValidate.remove(resource);
                    }
                }
            }
        }
    }
}
