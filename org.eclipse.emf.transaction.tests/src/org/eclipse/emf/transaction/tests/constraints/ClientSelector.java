/**
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   IBM - Initial API and implementation
 */
package org.eclipse.emf.transaction.tests.constraints;

import org.eclipse.emf.transaction.tests.ValidationRollbackTest;
import org.eclipse.emf.validation.model.IClientSelector;

/**
 * Simple flag-based client selector used by the validation test cases.
 *
 * @author Christian W. Damus (cdamus)
 */
public class ClientSelector
	implements IClientSelector {

	public boolean selects(Object object) {
		return ValidationRollbackTest.validationEnabled;
	}

}
