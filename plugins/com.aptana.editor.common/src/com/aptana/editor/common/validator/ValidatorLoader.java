/**
 * Aptana Studio
 * Copyright (c) 2005-2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the GNU Public License (GPL) v3 (with exceptions).
 * Please see the license.html included with this distribution for details.
 * Any modifications to this file must keep this entire header intact.
 */
package com.aptana.editor.common.validator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.RegistryFactory;

import com.aptana.core.util.StringUtil;
import com.aptana.editor.common.CommonEditorPlugin;

public class ValidatorLoader
{

	private static final String EXTENSION_POINT_ID = CommonEditorPlugin.PLUGIN_ID + ".validator"; //$NON-NLS-1$
	private static final String ELEMENT_VALIDATOR = "validator"; //$NON-NLS-1$
	private static final String ELEMENT_CONTENT_TYPE = "content-type"; //$NON-NLS-1$
	private static final String ATTR_NAME = "name"; //$NON-NLS-1$
	private static final String ATTR_ID = "id"; //$NON-NLS-1$
	private static final String ATTR_CONTENT_TYPE = "content-type"; //$NON-NLS-1$
	private static final String ATTR_MARKER_TYPE = "marker-type"; //$NON-NLS-1$

	// maps the content types by id
	private Map<String, ValidatorLanguage> contentTypes;
	// maps content type to the list of validators that support it
	private Map<String, List<ValidatorReference>> validators;

	private static ValidatorLoader instance;

	private ValidatorLoader()
	{
		contentTypes = new HashMap<String, ValidatorLanguage>();
		validators = new HashMap<String, List<ValidatorReference>>();
		readExtensionRegistry();
	}

	public static synchronized ValidatorLoader getInstance()
	{
		if (instance == null)
		{
			instance = new ValidatorLoader();
		}
		return instance;
	}

	public List<ValidatorLanguage> getLanguages()
	{
		List<ValidatorLanguage> result = new ArrayList<ValidatorLanguage>();
		result.addAll(contentTypes.values());
		return result;
	}

	public List<ValidatorReference> getValidators(String contentType)
	{
		List<ValidatorReference> list = validators.get(contentType);
		if (list == null)
		{
			return Collections.emptyList();
		}
		return Collections.unmodifiableList(list);
	}

	private void readExtensionRegistry()
	{
		IExtensionRegistry registry = RegistryFactory.getRegistry();
		if (registry != null)
		{
			IConfigurationElement[] elements = registry.getConfigurationElementsFor(EXTENSION_POINT_ID);
			processLanguages(elements);
			processValidators(elements);
		}
	}

	private void processLanguages(IConfigurationElement[] elements)
	{
		for (IConfigurationElement element : elements)
		{
			if (ELEMENT_CONTENT_TYPE.equals(element.getName()))
			{
				String id = element.getAttribute(ATTR_ID);
				if (StringUtil.isEmpty(id))
				{
					continue;
				}
				String name = element.getAttribute(ATTR_NAME);
				contentTypes.put(id, new ValidatorLanguage(id, name));
			}
		}
	}

	private void processValidators(IConfigurationElement[] elements)
	{
		for (IConfigurationElement element : elements)
		{
			if (ELEMENT_VALIDATOR.equals(element.getName()))
			{
				String name = element.getAttribute(ATTR_NAME);
				if (StringUtil.isEmpty(name))
				{
					continue;
				}
				String markerType = element.getAttribute(ATTR_MARKER_TYPE);
				if (StringUtil.isEmpty(markerType))
				{
					continue;
				}
				String contentType = element.getAttribute(ATTR_CONTENT_TYPE);
				ValidatorLanguage language = contentTypes.get(contentType);
				if (language == null)
				{
					continue;
				}
				ValidatorReference validator = new ValidatorReference(name, markerType, language, element);
				List<ValidatorReference> list = validators.get(contentType);
				if (list == null)
				{
					list = new ArrayList<ValidatorReference>();
					validators.put(contentType, list);
				}
				list.add(validator);
			}
		}
	}
}
