/*******************************************************************************
 * QMetry Automation Framework provides a powerful and versatile platform to
 * author
 * Automated Test Cases in Behavior Driven, Keyword Driven or Code Driven
 * approach
 * Copyright 2016 Infostretch Corporation
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT
 * OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE
 * You should have received a copy of the GNU General Public License along with
 * this program in the name of LICENSE.txt in the root folder of the
 * distribution. If not, see https://opensource.org/licenses/gpl-3.0.html
 * See the NOTICE.TXT file in root folder of this source files distribution
 * for additional information regarding copyright ownership and licenses
 * of other open source software / files used by QMetry Automation Framework.
 * For any inquiry or need additional information, please contact
 * support-qaf@infostretch.com
 *******************************************************************************/

package com.qmetry.qaf.automation.integration.qmetry.qmetry7;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.impl.LogFactoryImpl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qmetry.qaf.automation.integration.TestCaseResultUpdator;
import com.qmetry.qaf.automation.integration.TestCaseRunResult;
import com.qmetry.qaf.automation.integration.qmetry.QmetryWebserviceParameter.QmetryWSParameters;
import com.qmetry.qaf.automation.keys.ApplicationProperties;
import com.qmetry.qaf.automation.util.FileUtil;
import com.qmetry.qaf.automation.util.StringComparator;
import com.qmetry.qaf.automation.util.StringUtil;

/**
 * Implementation of {@link TestCaseResultUpdator} to update results on QMetry
 * 
 * @author anjali
 */
public class QMetry7ResultUpdator implements TestCaseResultUpdator {

	private static final Log logger = LogFactoryImpl.getLog(QMetry7ResultUpdator.class);

	@Override
	public boolean updateResult(Map<String, ? extends Object> params,
			TestCaseRunResult result, String log) {
		try {
			File[] attachments = null;
			long id = 0;
			String scriptName = "";
			boolean isRunid = false;
			id = getRunId(params);
			if (id > 0) {
				isRunid = true;
			} else {
				id = getTCID(params);
			}
			if (id == 0) {
				String sign = (String) params.get("sign");
				logger.error("no valid qmetry testcase mapping id found for " + sign);
				String sign1 = sign.split("instance:")[1].split("@")[0];
				scriptName = sign1 + "." + params.get("name");
			}
			logger.info("Updating result [" + result.toQmetry6() + "] for ["
					+ (String) params.get("sign")
					+ (id == 0 ? "] without test case Id. It will create automatically"
							: "] using " + (isRunid ? "runid [" : "tcid [") + id + "]"));

			try {
				updateResult(id, result, isRunid, scriptName);
			} catch (Exception e) {
				e.printStackTrace();
			}
			String suiteRunId = ApplicationProperties.INTEGRATION_PARAM_QMETRY_SUITERUNID
					.getStringVal();
			if (StringUtil.isNotBlank(suiteRunId)) {
				isRunid = true;
				QMetryRestWebservice integration = Qmetry7RestClient.getIntegration();
				JsonObject jsonTcs =
						new Gson().fromJson(integration.getTestCaseRunID(suiteRunId),
								JsonElement.class).getAsJsonObject();
				JsonArray arrTCJson = jsonTcs.get("data").getAsJsonArray();
				for (JsonElement jsonElement : arrTCJson) {
					if (jsonElement.getAsJsonObject().get("tcID").toString()
							.equalsIgnoreCase(String.valueOf(id))) {
						id = jsonElement.getAsJsonObject().get("tcRunID").getAsLong();
						break;
					}
				}
			}
			if (ApplicationProperties.INTEGRATION_TOOL_QMETRY_UPLOADATTACHMENTS
					.getBoolenVal(true)) {
				addAttachments(log, (String) params.get("name"), id, isRunid);
				try {
					attachments =
							(File[]) params.get(QmetryWSParameters.Attachments.name());
					addAttachments(id, isRunid, attachments);
				} catch (Exception e) {
					logger.error(e);
				}

			}
			return true;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	private static Qmetry7RestClient util = Qmetry7RestClient.getInstance();

	private void updateResult(long id, TestCaseRunResult result, boolean isRunid,
			String scriptName) {

		boolean retVal = isRunid
				? util.executeTestCaseUsingRunIdAndTCId(id, result.toQmetry6())
				: (id == 0 ? util.executeTestCaseWithoutID(scriptName, result.toQmetry6())
						: util.executeTestCase(id, result.toQmetry6()));;
		logger.info("Update result status using " + (isRunid ? "runid "
				: (id == 0 ? "without tc_id" : "tcid " + id) + " is: " + retVal));

	}

	private void addAttachments(String log, String methodName, long id, boolean isRunid) {
		if (StringUtil.isNotBlank(log)) {
			try {
				File logFile = FileUtil
						.createTempFile("log_" + System.currentTimeMillis(), "htm");
				FileUtil.writeStringToFile(logFile, log, "UTF-8");
				addAttachments(id, isRunid, logFile);

			} catch (IOException e) {
				logger.error(e);
			}
		}
		File dir = new File(ApplicationProperties.SCREENSHOT_DIR.getStringVal(""));
		if (dir.exists()) {
			File[] screenshots = FileUtil.listFilesAsArray(dir, methodName,
					StringComparator.Prefix, true);
			System.out.println(screenshots.length + " screenshots will be attached");
			addAttachments(id, isRunid, screenshots);
		}
	}

	public void addAttachments(long id, boolean isRunid, File... file) {
		if ((file != null) && (file.length > 0)) {
			for (File f : file) {
				int retval = isRunid ? util.attachFileUsingRunId(id, 0, f)
						: util.attachFile(id, 0, f);
				logger.info("upload staus for [" + f.getName() + "]using "
						+ (isRunid ? "runid[" : "tcid[") + id + "] is: " + retval);
			}
		}

	}

	private int getRunId(Map<String, ? extends Object> params) {
		Integer[] runids = ((null != params) && params.containsKey("testScriptName"))
				? extractNums((String) params.get("runId")) : null;
		if (((null != runids) && (runids.length > 0))) {
			return runids[0].intValue();
		}
		return 0;
	}

	private int getTCID(Map<String, ? extends Object> params) {
		Integer tcids[] = (null != params) && params.containsKey("TC_ID")
				? extractNums(Qmetry7RestClient.getIntegration()
						.getTCIDusingAttribute((String) params.get("TC_ID"), "entityKey"))
				: extractNums(Qmetry7RestClient.getIntegration()
						.getTCIDusingAttribute((String) params.get("name"), "name"));

		if ((tcids != null) && (tcids.length > 0)) {
			return tcids[0].intValue();
		}
		return 0;
	}

	public static Integer[] extractNums(String s) {
		ArrayList<Integer> lst = new ArrayList<Integer>();
		Pattern p = Pattern.compile("(\\d)+");
		Matcher m = p.matcher(s);
		while (m.find()) {
			lst.add(Integer.parseInt(m.group()));
		}
		return lst.toArray(new Integer[lst.size()]);
	}

	@Override
	public String getToolName() {
		return "QMetry";
	}

}
