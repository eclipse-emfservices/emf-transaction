/**
 * <copyright>
 *
 * Copyright (c) 2007 IBM Corporation and others.
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
 * $Id: WorkspaceValidateEditSupport.java,v 1.1 2007/10/03 20:17:29 cdamus Exp $
 */

package org.eclipse.emf.workspace.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.util.ValidateEditSupport;


/**
 * <p>
 * An implementation of the transaction validate-edit support that uses the
 * Eclipse Platform's
 * {@linkplain IWorkspace#validateEdit(org.eclipse.core.resources.IFile[], Object) Workspace API}
 * to validate edits.
 * </p><p>
 * Clients may find it useful to install
 * {@linkplain TransactionalEditingDomain.DefaultOptions default transaction options}
 * specifying an instance of this class and an appropriate SWT shell for
 * validate-edit support.
 * </p>
 * 
 * @author Christian W. Damus (cdamus)
 * 
 * @since 1.2
 */
public class WorkspaceValidateEditSupport
    extends ValidateEditSupport.Default {

    protected IStatus doValidateEdit(Transaction transaction,
            Collection resources, Object context) {
        
        IFile[] files = getFiles(resources);
        
        return ResourcesPlugin.getWorkspace().validateEdit(files, context);
    }
    
    protected IFile[] getFiles(Collection resources) {
        List result = new BasicEList();
        for (Iterator iter = resources.iterator(); iter.hasNext();) {
            IFile file = WorkspaceSynchronizer.getFile((Resource) iter.next());
            
            if (file != null) {
                result.add(file);
            }
        }
        
        return (IFile[]) result.toArray(new IFile[result.size()]);
    }
}
