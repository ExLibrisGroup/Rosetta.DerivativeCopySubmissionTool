package derivativeCopySubmissionTool;

import gov.loc.mets.MetsDocument;
import gov.loc.mets.MetsType.FileSec.FileGrp;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;

import com.exlibris.core.infra.common.shared.dataObjects.KeyValuePair;
import com.exlibris.core.sdk.consts.Enum.UsageType;
import com.exlibris.core.sdk.parser.IEParserException;
import com.exlibris.core.sdk.strings.StringUtils;
import com.exlibris.core.sdk.utils.DepositDirUtil;
import com.exlibris.core.sdk.utils.FileUtil;
import com.exlibris.digitool.common.dnx.DnxDocument;
import com.exlibris.digitool.common.dnx.DnxDocumentFactory;
import com.exlibris.digitool.common.dnx.DnxDocumentHelper;
import com.exlibris.dps.sdk.deposit.IEParser;
import com.exlibris.dps.sdk.deposit.IEParserFactory;

public class DerivativeCopySubmissionTool {

	private static final String PID = "PID";
	private static final String DERIVATIVE_COPY = "DERIVATIVE_COPY";
	private static final String DERIVATIVE_COPY_LABEL = "Derivative Copy";
	public static final String LOCKED = "LOCKED";

	private String iePid;
	private String repEntityType;
	private String repCode;
	private String sourcePath;
	private String targetPath;
	private String streamsPath;

	public DerivativeCopySubmissionTool(String iePid, String repEntityType, String repCode, String sourcePath, String targetPath) {
		this.iePid = iePid;
		this.repEntityType = repEntityType;
		this.repCode = repCode;
		this.sourcePath = sourcePath;
		this.targetPath = targetPath;
	}

	public void run() throws Exception,	IEParserException, IOException, XmlException {
		IEParser ieParser = IEParserFactory.create();

		/** Prepare IE DNX with InternalIdentifier Section **/
		DnxDocument ieDnx = DnxDocumentFactory.getInstance().createDnxDocument();
		DnxDocumentHelper ieDnxDocumentHelper = new DnxDocumentHelper(ieDnx);
		ieDnxDocumentHelper.new InternalIdentifier(PID, iePid);
		ieParser.setIeDnx(ieDnx);

		/** Prepare Representation DNX with GeneralRepCharacteristics Section **/
		FileGrp fileGrp = ieParser.addNewFileGrp(UsageType.VIEW, DERIVATIVE_COPY);
		DnxDocumentHelper repDnxDocumentHelper = new DnxDocumentHelper(ieParser.getFileGrpDnx(fileGrp.getID()));
		if (StringUtils.notEmptyString(repEntityType)) {
			repDnxDocumentHelper.getGeneralRepCharacteristics().setRepresentationEntityType(repEntityType);
		}
		if (StringUtils.notEmptyString(repCode)) {
			repDnxDocumentHelper.getGeneralRepCharacteristics().setRepresentationCode(repCode);
		}
		ieParser.setFileGrpDnx(repDnxDocumentHelper.getDocument(), fileGrp.getID());

		/** Prepare Target Directory **/
		File mainDirectory = new File(FilenameUtils.concat(targetPath, iePid));
		if (mainDirectory.exists()) {
			mainDirectory.delete();
		}
		mainDirectory.mkdir();

		/** Lock **/
		File lockFile = new File(FilenameUtils.concat(mainDirectory.getPath(), LOCKED));
		lockFile.createNewFile();

		File contentDirectory = new File(FilenameUtils.concat(mainDirectory.getPath(), DepositDirUtil.CONTENT_DIR));
		contentDirectory.mkdir();
		File streamsDirectory = new File(FilenameUtils.concat(contentDirectory.getPath(), DepositDirUtil.STREAMS_DIR));
		streamsDirectory.mkdir();
		streamsPath = streamsDirectory.getPath();

		/** Handle Files **/
		File filesDirectory = new File(sourcePath);
		FileUtil.copyFilesToDirectory(filesDirectory, streamsDirectory, true);
		Map<String, KeyValuePair<String, String>> filesStreams = handleFiles(ieParser, fileGrp, streamsDirectory);

		/** Generate Logical StructMap **/
		ieParser.buildLogicalStructMap(fileGrp.getID(), filesStreams, DERIVATIVE_COPY_LABEL);

		/** Create XML File **/
		MetsDocument metsDoc = MetsDocument.Factory.parse(ieParser.toXML());
		File xmlFile = new File(FilenameUtils.concat(contentDirectory.getAbsolutePath(), DepositDirUtil.METS_XML));
		XmlOptions opt = new XmlOptions();
		opt.setSavePrettyPrint();
		FileUtil.writeFile(xmlFile, metsDoc.xmlText(opt));

		/** Unlock **/
		lockFile.delete();
	}

	private Map<String, KeyValuePair<String, String>> handleFiles(IEParser ieParser, FileGrp fileGrp, File streamsDirectory) throws IEParserException {
		File[] files = streamsDirectory.listFiles();
		Arrays.sort(files);
		Map<String, KeyValuePair<String, String>> filesStreams = new LinkedHashMap<String, KeyValuePair<String, String>>();
		for (File file : files) {
			if (file.isFile()) {
				String filePid = ieParser.getFileId(fileGrp);
				String fileLabel = FilenameUtils.removeExtension(file.getName());
				String fileLocation = file.getPath().substring(streamsPath.length()+1);
				filesStreams.put(filePid, new KeyValuePair<String, String>(fileLabel, fileLocation));
				ieParser.addNewFile(fileGrp, null, fileLocation,  fileLabel, filePid, null, null, false);
			} else {
				filesStreams.putAll(handleFiles(ieParser, fileGrp, file));
			}
		}
		return filesStreams;
	}

	public static void main(String[] args) {

		try {

			String iePid = args[0];
			String repEntityType = args[1];
			String repCode = args[2];
			String sourcePath = args[3];
			String targetPath = args[4];

			DerivativeCopySubmissionTool tool = new DerivativeCopySubmissionTool(iePid, repEntityType, repCode, sourcePath, targetPath);
			tool.run();

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}


