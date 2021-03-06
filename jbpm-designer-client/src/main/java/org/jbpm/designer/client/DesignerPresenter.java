package org.jbpm.designer.client;

import javax.enterprise.context.Dependent;
import javax.enterprise.event.Event;
import javax.inject.Inject;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.FrameElement;
import com.google.gwt.dom.client.ScriptElement;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.IsWidget;
import org.jboss.errai.bus.client.api.RemoteCallback;
import org.jboss.errai.ioc.client.api.Caller;
import org.jbpm.designer.client.type.Bpmn2Type;
import org.jbpm.designer.service.DesignerAssetService;
import org.uberfire.backend.vfs.Path;
import org.uberfire.backend.vfs.PathFactory;
import org.uberfire.client.annotations.OnStart;
import org.uberfire.client.annotations.WorkbenchEditor;
import org.uberfire.client.annotations.WorkbenchPartTitle;
import org.uberfire.client.annotations.WorkbenchPartView;
import org.uberfire.client.mvp.PlaceManager;
import org.uberfire.client.mvp.UberView;
import org.uberfire.mvp.PlaceRequest;
import org.uberfire.mvp.impl.DefaultPlaceRequest;
import org.uberfire.mvp.impl.PathPlaceRequest;
import org.uberfire.workbench.events.ResourceUpdatedEvent;
import org.uberfire.backend.vfs.VFSService;

@Dependent
@WorkbenchEditor(identifier = "jbpm.designer", supportedTypes = { Bpmn2Type.class })
public class DesignerPresenter {

    public interface View
            extends
            UberView<DesignerPresenter> {

        void setEditorID( final String id );

        void startDesignerInstance( final String editorId );
    }

    @Inject
    private DesignerView view;

    @Inject
    private Caller<DesignerAssetService> assetService;

    @Inject
    private PlaceManager placeManager;

    @Inject
    private Event<ResourceUpdatedEvent> resourceUpdatedEvent;

    @Inject
    private Caller<VFSService> vfsServices;

    private Path path;
    private PlaceRequest place;

    @OnStart
    public void onStart( final Path path,
                         final PlaceRequest place ) {
        this.path = path;
        this.place = place;
        this.publishOpenInTab(this);
        this.publishSignalOnAssetUpdate(this);
        if ( path != null ) {
            assetService.call( new RemoteCallback<String>() {
                @Override
                public void callback( final String editorID ) {
                    view.setEditorID( editorID );
                    String url = GWT.getHostPageBaseURL().replaceFirst( "/" + GWT.getModuleName() + "/", "" );
                    assetService.call( new RemoteCallback<String>() {
                        @Override
                        public void callback( String editorBody ) {
                            // remove the open+close script tags before injecting
                            editorBody = editorBody.replaceAll( "<script type=\"text/javascript\">", "" );
                            editorBody = editorBody.replaceAll( "</script>", "" );

                            final Document doc = Document.get();
                            final FrameElement editorInlineFrame = (FrameElement) doc.getElementById( editorID );
                            appendScriptSource( editorInlineFrame, editorBody );
                        }

                    } ).loadEditorBody( path, editorID, url, place );
                }
            } ).getEditorID();
        }
    }

    @WorkbenchPartTitle
    public String getName() {
        return this.path.getFileName();
    }

    @WorkbenchPartView
    public IsWidget getView() {
        return view;
    }

    private void appendScriptSource( final FrameElement element,
                                     final String source ) {
        final ScriptElement scriptElement = Document.get().createScriptElement( source );
        scriptElement.setType( "text/javascript" );
        element.getContentDocument().getDocumentElement().getElementsByTagName( "head" ).getItem( 0 ).appendChild( scriptElement );
    }

//    private native String getPageURL()  /*-{
//        return $wnd.location.protocol + "//" + $wnd.location.host;
//    }-*/;

    private native void publishOpenInTab(DesignerPresenter dp)/*-{
        $wnd.designeropenintab = function (filename, uri) {
            dp.@org.jbpm.designer.client.DesignerPresenter::openInTab(Ljava/lang/String;Ljava/lang/String;)(filename, uri);
        }
    }-*/;

    private native void publishSignalOnAssetUpdate(DesignerPresenter dp)/*-{
        $wnd.designersignalassetupdate = function (uri) {
            dp.@org.jbpm.designer.client.DesignerPresenter::assetUpdateEvent(Ljava/lang/String;)(uri);
        }
    }-*/;

    public void assetUpdateEvent(String uri) {
        vfsServices.call( new RemoteCallback<Path>() {
            @Override
            public void callback( final Path mypath ) {
                resourceUpdatedEvent.fire( new ResourceUpdatedEvent(mypath) );
            }
        } ).get( uri );
    }

    public void openInTab(String filename, String uri) {
        PlaceRequest placeRequestImpl = new PathPlaceRequest(
                PathFactory.newPath(this.path.getFileSystem(), filename, uri)
        );
        placeRequestImpl.addParameter("uuid", uri);
        placeRequestImpl.addParameter("profile", "jbpm");
        this.placeManager.goTo(placeRequestImpl);
    }
}
