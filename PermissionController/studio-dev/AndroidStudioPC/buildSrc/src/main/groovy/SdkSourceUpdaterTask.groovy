import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Gradle task to update sources link in sdk
 */
class SdkSourceUpdaterTask extends DefaultTask  {

    @InputDirectory
    File androidRoot

    @TaskAction
    void execute() throws Exception {

        File sdkDef = new File(System.getProperty("user.home"),
                ".AndroidStudioSystemUI/config/options/jdk.table.xml");
        if (!sdkDef.exists()) {
            throw new IllegalStateException("Sdk config file not found at " + sdkDef);
        }

        File sdkPath = new File(androidRoot, "out/gradle/MockSdk");
        if (!sdkPath.exists()) {
            throw new IllegalStateException("SDK not found at " + sdkPath);
        }
        sdkPath = sdkPath.getCanonicalFile();
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(sdkDef);

        NodeList list = doc.getElementsByTagName("jdk");
        for (int i = 0; i < list.getLength(); i++) {
            Node node = list.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                Element homePath = findFirstElement(element, "homePath");
                if (homePath == null) {
                    continue;
                }
                String pathValue = homePath.getAttribute("value");
                if (pathValue == null || pathValue.isBlank()) {
                    continue;
                }
                File file = new File(pathValue);
                if (!file.getCanonicalPath().equals(sdkPath.getCanonicalPath())) {
                    continue;
                }

                // Found the right SDK
                Element sourcePath = findFirstElement(element, "sourcePath");
                if (sourcePath == null) {
                    // TODO: Add source path
                    continue;
                }

                while (sourcePath.hasChildNodes())
                    sourcePath.removeChild(sourcePath.getFirstChild());

                // Create root
                Element el = createRoot(doc, "type", "composite");
                sourcePath.appendChild(el);

                // Create paths
                el.appendChild(createRoot(doc, "type", "simple", "url", "file://"
                        + new File(androidRoot, "frameworks/base/core/java").getCanonicalPath()));
                el.appendChild(createRoot(doc, "type", "simple", "url", "file://"
                        + new File(androidRoot, "frameworks/base/graphics/java").getCanonicalPath()));
            }
        }

        // Write the xml
        TransformerFactory.newInstance().newTransformer()
                .transform(new DOMSource(doc), new StreamResult(sdkDef))

        System.out.println("======================================")
        System.out.println("======================================")
        System.out.println("       Android sources linked")
        System.out.println("Restart IDE for changes to take effect")
        System.out.println("======================================")
        System.out.println("======================================")
    }

    private Element createRoot(Document doc, String... attrs) {
        Element el = doc.createElement("root");
        for (int i = 0; i < attrs.length; i += 2) {
            el.setAttribute(attrs[i], attrs[i + 1]);
        }
        return el;
    }

    private Element findFirstElement(Element node, String tag) {
        NodeList paths = node.getElementsByTagName(tag);
        if (paths.getLength() < 1) {
            return null;
        }
        Node n = paths.item(0);
        if (n.getNodeType() != Node.ELEMENT_NODE) {
            return null;
        }
        return (Element) n;
    }
}