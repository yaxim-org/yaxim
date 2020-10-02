package org.yaxim.androidclient.service;

import android.app.Activity;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.lang.ref.WeakReference;

import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.StanzaError;
import org.jivesoftware.smackx.commands.AdHocCommand;
import org.jivesoftware.smackx.commands.AdHocCommandManager;
import org.jxmpp.jid.impl.JidCreate;
import org.yaxim.androidclient.R;
import org.yaxim.androidclient.data.ChatHelper;
import org.yaxim.androidclient.data.YaximConfiguration;
import org.yaxim.androidclient.util.XMPPHelper;

public class InvitationTask extends AsyncTask<Void, String, InvitationTask.InvitationResponse> {
    private static final String TAG = "yaxim.InvitationTask";

    private WeakReference<Activity> ctx;
    private YaximConfiguration config;
    private Smackable smackable;
    private Toast status;

    public InvitationTask(Activity ctx, YaximConfiguration config, Smackable smackable) {
        this.ctx = new WeakReference(ctx);
        this.config = config;
        this.smackable = smackable;
    }

    @Override
    protected InvitationResponse doInBackground(Void... params) {
        try {
            XMPPConnection connection = smackable.getConnection();

			AdHocCommandManager mgr = AdHocCommandManager.getAddHocCommandsManager(connection);
            AdHocCommand cmd = mgr.getRemoteCommand(JidCreate.domainBareFromOrThrowUnchecked(config.server), "urn:xmpp:invite#invite");
            try {
                cmd.execute();
            } catch (XMPPException.XMPPErrorException e) {
                if (e.getStanzaError().getCondition() == StanzaError.Condition.service_unavailable)
                    return failResponse((String)null);
                else throw e;
            }
            if (cmd.isCompleted()) {
                String landing = cmd.getForm().getField("landing-url").getFirstValue();
                if (TextUtils.isEmpty(landing))
                    landing = cmd.getForm().getField("uri").getFirstValue().replace("xmpp:", "https://yax.im/i/#");
                return new InvitationResponse(true, landing);
            }
            return failResponse("Ad-Hoc command did not complete: " + cmd.getStatus());

        } catch (Exception e) {
			return failResponse(e);
		}
    }

    private void showLongToast(String toast, boolean is_error) {
        Activity act = ctx.get();
        if (act != null) {
            if (is_error)
                toast = act.getString(R.string.conn_error, toast);
            status = Toast.makeText(act, toast, Toast.LENGTH_LONG);
            status.show();
        }
    }

    @Override
    protected void onProgressUpdate(String... values) {
        if (status != null)
            status.cancel();
        showLongToast(values[0], false);
    }

    @Override
    protected void onPreExecute() { }

    @Override
    protected void onPostExecute(InvitationResponse response) {
        String landing_page = null;
        if (status != null)
            status.cancel();
        if (response.success) {
            landing_page = response.response;
        } else {
            if (response.response != null)
                showLongToast(response.response, !response.success);
            landing_page = XMPPHelper.createInvitationLinkHTTPS(config.jabberID,
                    config.createInvitationCode());
        }
        Activity act = ctx.get();
        if (act != null && !act.isFinishing())
            ChatHelper.showQrDialog(act, config.jabberID, landing_page, act.getString(R.string.Menu_send_invitation));
    }

    private InvitationResponse failResponse(String reason) {
        return new InvitationResponse(false, reason);
    }
    private InvitationResponse failResponse(Throwable exception) {
        exception.printStackTrace();
        return new InvitationResponse(false, exception.getLocalizedMessage());
    }


    public class InvitationResponse {
        boolean success;
        String response;

        public InvitationResponse(boolean success, String response) {
            this.success = success;
            this.response = response;
            if (!success)
                Log.e("yaxim.InvitationTask", this.toString());
        }
    }
}
