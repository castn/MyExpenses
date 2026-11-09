/*   This file is part of My Expenses.
 *   My Expenses is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   My Expenses is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with My Expenses.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.totschnig.myexpenses.activity;

import static org.totschnig.myexpenses.activity.ConstantsKt.CALCULATOR_REQUEST;
import static org.totschnig.myexpenses.activity.ConstantsKt.CONTRIB_REQUEST;
import static org.totschnig.myexpenses.activity.ContribInfoDialogActivity.KEY_FEATURE;
import static org.totschnig.myexpenses.preference.PrefKey.UI_FONT_SIZE;
import static org.totschnig.myexpenses.provider.ConstantsKt.KEY_AMOUNT;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import androidx.core.content.IntentCompat;
import org.totschnig.myexpenses.model.ContribPayload;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.dialog.ProgressDialogFragment;
import org.totschnig.myexpenses.model.ContribFeature;
import org.totschnig.myexpenses.model.CurrencyUnit;
import org.totschnig.myexpenses.ui.AmountInput;

import java.math.BigDecimal;

import javax.inject.Inject;
public abstract class ProtectedFragmentActivity extends BaseActivity
    implements
    ProgressDialogFragment.ProgressDialogListener {

  public static final String EDIT_COLOR_DIALOG = "editColorDialog";

  protected ColorStateList textColorSecondary;

  @Inject
  protected SharedPreferences settings;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    settings.registerOnSharedPreferenceChangeListener(this);
    TypedArray themeArray = getTheme().obtainStyledAttributes(new int[]{android.R.attr.textColorSecondary});
    textColorSecondary = themeArray.getColorStateList(0);
    themeArray.recycle();
  }

  @Override
  protected void attachBaseContext(Context newBase) {
    super.attachBaseContext(newBase);
    final MyApplication application = MyApplication.Companion.getInstance();
    final int customFontScale = application.getAppComponent().prefHandler().getInt(UI_FONT_SIZE, 0);
    if (customFontScale > 0) {
      Configuration config = new Configuration();
      config.fontScale = getFontScale(customFontScale, application.getContentResolver());
      applyOverrideConfiguration(config);
    }
    featureManager.initActivity(this);
  }

  private float getFontScale(int customFontScale, ContentResolver contentResolver) {
    return Settings.System.getFloat(contentResolver, Settings.System.FONT_SCALE, 1.0f) * (1 + customFontScale / 10F);
  }

  @Override
  protected void injectDependencies() {
    ((MyApplication) getApplicationContext()).getAppComponent().inject(this);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    settings.unregisterOnSharedPreferenceChangeListener(this);
  }

  @Override
  protected void onResume() {
    super.onResume();
    getCrashHandler().addBreadcrumb(getClass().getSimpleName());
  }

  @Override
  public void onProgressDialogDismiss() {
  }

  @Override
  public boolean onCreateOptionsMenu(@NonNull Menu menu) {
    super.onCreateOptionsMenu(menu);
    inflateHelpMenu(menu);
    return true;
  }

  protected void inflateHelpMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.help, menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int itemId = item.getItemId();
    if (itemId != 0) {
      //isChecked reports the value at the moment the item is clicked, we dispatch command with the
      //requested new value
      if (dispatchCommand(itemId, item.isCheckable() ? !item.isChecked() : null)) {
        return true;
      }
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode,
                                  Intent intent) {
    super.onActivityResult(requestCode, resultCode, intent);
    if (requestCode == CONTRIB_REQUEST && intent != null) {
      ContribFeature contribFeature = ContribFeature.valueOf(intent.getStringExtra(KEY_FEATURE));
      if (resultCode == RESULT_OK) {
        ((ContribIFace) this).contribFeatureCalled(contribFeature,
            IntentCompat.getParcelableExtra(intent, ContribInfoDialogActivity.KEY_TAG, ContribPayload.class));
      } else if (resultCode == RESULT_CANCELED) {
        this.contribFeatureNotCalled(contribFeature);
      }
    }
    if (resultCode == RESULT_OK && requestCode == CALCULATOR_REQUEST && intent != null) {
      View target = findViewById(intent.getIntExtra(CalculatorInput.EXTRA_KEY_INPUT_ID, 0));
      if (target instanceof AmountInput) {
        ((AmountInput) target).setAmount(new BigDecimal(intent.getStringExtra(KEY_AMOUNT)), false, false);
      } else {
        showSnackBar("CALCULATOR_REQUEST launched with incorrect EXTRA_KEY_INPUT_ID");
      }
    }
  }

  @Override
  public void onCurrencySelectionChanged(@NonNull CurrencyUnit currencyUnit) {
  }

  public enum ThemeType {
    dark, light
  }
}
